package backend.knowledge_api_service.service;

import backend.knowledge_api_service.dto.request.ChatRequest;
import backend.knowledge_api_service.dto.request.FeedbackRequest;
import backend.knowledge_api_service.dto.response.*;
import backend.knowledge_api_service.entity.ChatMessageEntity;
import backend.knowledge_api_service.entity.Conversation;
import backend.knowledge_api_service.exception.ResourceNotFoundException;
import backend.knowledge_api_service.filter.RequestIdFilter;
import backend.knowledge_api_service.model.AuditAction;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.MessageRole;
import backend.knowledge_api_service.repository.ChatMessageRepository;
import backend.knowledge_api_service.repository.ConversationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {

    public static final String NOT_FOUND_ANSWER = "I couldn't find this in the documents you have access to.";

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private ChatRateLimiter chatRateLimiter;
    private AuditEventPublisher auditEventPublisher;

    public ChatService(
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            AiServiceClient aiServiceClient,
            ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.aiServiceClient = aiServiceClient;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    public void setChatRateLimiter(ChatRateLimiter chatRateLimiter) {
        this.chatRateLimiter = chatRateLimiter;
    }

    @Autowired(required = false)
    public void setAuditEventPublisher(AuditEventPublisher auditEventPublisher) {
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public ChatResponse chat(ChatRequest request) {
        RequestIdFilter.CallerInfo caller = RequestIdFilter.getCurrentCaller();
        if (chatRateLimiter != null) {
            chatRateLimiter.checkRateLimit(caller.userId());
        }

        UUID userId = caller.userId();
        Conversation conversation = loadOrCreateConversation(request, userId);
        List<AiServiceClient.HistoryMessage> history = loadLast6HistoryMessages(conversation.getId());

        ChatMessageEntity userMsg = ChatMessageEntity.builder()
                .conversationId(conversation.getId())
                .role(MessageRole.USER)
                .content(request.getMessage())
                .citations("[]")
                .build();
        chatMessageRepository.save(userMsg);

        List<Department> effectiveDepartments = computeEffectiveDepartments(request, caller);
        UUID queryId = UUID.randomUUID();

        AiServiceClient.InternalAnswerResponse aiAnswer;
        if (effectiveDepartments.isEmpty()) {
            aiAnswer = AiServiceClient.InternalAnswerResponse.builder()
                    .queryId(queryId)
                    .answer(NOT_FOUND_ANSWER)
                    .answered(false)
                    .confidence(0.0)
                    .citations(Collections.emptyList())
                    .latencyMs(0)
                    .build();
        } else {
            AiServiceClient.InternalAnswerRequest answerRequest = AiServiceClient.InternalAnswerRequest.builder()
                    .queryId(queryId)
                    .query(request.getMessage())
                    .allowedDepartments(effectiveDepartments)
                    .history(history)
                    .topK(6)
                    .build();
            aiAnswer = aiServiceClient.answer(answerRequest);
        }

        List<CitationDto> citations = aiAnswer.getCitations() != null
                ? aiAnswer.getCitations()
                : Collections.emptyList();

        boolean answered = Boolean.TRUE.equals(aiAnswer.getAnswered());
        String answerText = aiAnswer.getAnswer() != null && !aiAnswer.getAnswer().isBlank()
                ? aiAnswer.getAnswer()
                : NOT_FOUND_ANSWER;
        double confidence = aiAnswer.getConfidence() != null ? aiAnswer.getConfidence() : 0.0;
        int latencyMs = aiAnswer.getLatencyMs() != null ? aiAnswer.getLatencyMs() : 0;

        String citationsJson = serializeCitations(citations);
        ChatMessageEntity assistantMsg = ChatMessageEntity.builder()
                .conversationId(conversation.getId())
                .role(MessageRole.ASSISTANT)
                .content(answerText)
                .citations(citationsJson)
                .queryId(queryId)
                .answered(answered)
                .build();
        ChatMessageEntity savedAssistantMsg = chatMessageRepository.save(assistantMsg);

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        publishQueryAskedAudit(queryId, conversation.getId(), request.getMessage(),
                effectiveDepartments, answered, confidence, latencyMs, citations);

        return ChatResponse.builder()
                .queryId(queryId)
                .conversationId(conversation.getId())
                .messageId(savedAssistantMsg.getId())
                .answer(savedAssistantMsg.getContent())
                .answered(savedAssistantMsg.getAnswered())
                .confidence(confidence)
                .citations(citations)
                .latencyMs(latencyMs)
                .build();
    }

    /**
     * Milestone 4: Server-Sent Events (SSE) chat relay (contracts/streaming.md).
     * Emits: meta -> token* -> citations -> done (or error), and persists the assistant message.
     */
    @Transactional
    public SseEmitter streamChat(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        try {
            ChatResponse chatRes = chat(request);

            // 1. event: meta
            emitter.send(SseEmitter.event()
                    .name("meta")
                    .data(objectMapper.writeValueAsString(Map.of(
                            "queryId", chatRes.getQueryId().toString(),
                            "conversationId", chatRes.getConversationId().toString(),
                            "messageId", chatRes.getMessageId().toString()))));

            // 2. event: token (split answer into stream chunks)
            String answer = chatRes.getAnswer() != null ? chatRes.getAnswer() : "";
            for (String tokenChunk : splitIntoTokens(answer)) {
                emitter.send(SseEmitter.event()
                        .name("token")
                        .data(objectMapper.writeValueAsString(Map.of("text", tokenChunk))));
            }

            // 3. event: citations
            emitter.send(SseEmitter.event()
                    .name("citations")
                    .data(objectMapper.writeValueAsString(Map.of(
                            "citations", chatRes.getCitations() != null ? chatRes.getCitations() : Collections.emptyList()))));

            // 4. event: done
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(objectMapper.writeValueAsString(Map.of(
                            "answered", Boolean.TRUE.equals(chatRes.getAnswered()),
                            "confidence", chatRes.getConfidence() != null ? chatRes.getConfidence() : 0.0,
                            "latencyMs", chatRes.getLatencyMs() != null ? chatRes.getLatencyMs() : 0))));

            emitter.complete();
        } catch (Exception ex) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(objectMapper.writeValueAsString(Map.of(
                                "code", "STREAM_ERROR",
                                "message", ex.getMessage() != null ? ex.getMessage() : "Stream interrupted",
                                "traceId", RequestIdFilter.getCurrentTraceId()))));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    @Transactional
    public void submitFeedback(UUID messageId, FeedbackRequest request) {
        ChatMessageEntity msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("MESSAGE_NOT_FOUND",
                        "Chat message not found with ID: " + messageId));

        msg.setFeedback(request.getRating().toUpperCase(Locale.ROOT));
        msg.setFeedbackComment(request.getComment());
        chatMessageRepository.save(msg);

        if (auditEventPublisher != null) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("messageId", messageId.toString());
            meta.put("rating", msg.getFeedback());
            if (request.getComment() != null) {
                meta.put("comment", request.getComment());
            }
            auditEventPublisher.publish(AuditAction.FEEDBACK_SUBMITTED, "CONVERSATION",
                    msg.getConversationId().toString(), meta);
        }
    }

    @Transactional(readOnly = true)
    public ConversationPageResponse listConversations(int page, int size) {
        UUID userId = RequestIdFilter.getCurrentCaller().userId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<Conversation> convPage = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable);

        List<ConversationDto> items = convPage.getContent().stream()
                .map(c -> ConversationDto.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .createdAt(c.getCreatedAt())
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        return ConversationPageResponse.builder()
                .page(convPage.getNumber())
                .size(convPage.getSize())
                .totalItems(convPage.getTotalElements())
                .totalPages(convPage.getTotalPages())
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse getConversation(UUID conversationId) {
        UUID userId = RequestIdFilter.getCurrentCaller().userId();
        Conversation conv = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CONVERSATION_NOT_FOUND",
                        "Conversation not found with ID: " + conversationId));

        List<ChatMessageDto> messages = chatMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());

        return ConversationDetailResponse.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .messages(messages)
                .build();
    }

    @Transactional
    public void deleteConversation(UUID conversationId) {
        UUID userId = RequestIdFilter.getCurrentCaller().userId();
        Conversation conv = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CONVERSATION_NOT_FOUND",
                        "Conversation not found with ID: " + conversationId));
        List<ChatMessageEntity> msgs = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        chatMessageRepository.deleteAll(msgs);
        conversationRepository.delete(conv);
    }

    private Conversation loadOrCreateConversation(ChatRequest request, UUID userId) {
        if (request.getConversationId() != null) {
            return conversationRepository.findByIdAndUserId(request.getConversationId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("CONVERSATION_NOT_FOUND",
                            "Conversation not found with ID: " + request.getConversationId()));
        }
        String title = truncateTitle(request.getMessage());
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .title(title)
                .build();
        return conversationRepository.save(conversation);
    }

    private List<AiServiceClient.HistoryMessage> loadLast6HistoryMessages(UUID conversationId) {
        List<ChatMessageEntity> recentDesc = chatMessageRepository
                .findTop6ByConversationIdOrderByCreatedAtDesc(conversationId);
        List<ChatMessageEntity> recentChronological = new ArrayList<>(recentDesc);
        Collections.reverse(recentChronological);

        return recentChronological.stream()
                .map(m -> AiServiceClient.HistoryMessage.builder()
                        .role(m.getRole().name())
                        .content(m.getContent())
                        .build())
                .collect(Collectors.toList());
    }

    private List<Department> computeEffectiveDepartments(ChatRequest request, RequestIdFilter.CallerInfo caller) {
        List<Department> callerAllowed = caller.allowedDepartments();
        if (request.getDepartments() != null && !request.getDepartments().isEmpty()) {
            return request.getDepartments().stream()
                    .filter(callerAllowed::contains)
                    .distinct()
                    .collect(Collectors.toList());
        }
        return new ArrayList<>(callerAllowed);
    }

    private void publishQueryAskedAudit(
            UUID queryId,
            UUID conversationId,
            String queryText,
            List<Department> allowedDepartments,
            boolean answered,
            double confidence,
            int latencyMs,
            List<CitationDto> citations) {
        if (auditEventPublisher == null) {
            return;
        }
        List<String> citedDocIds = citations.stream()
                .map(CitationDto::getDocumentId)
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("queryId", queryId.toString());
        metadata.put("queryText", queryText);
        metadata.put("conversationId", conversationId.toString());
        metadata.put("allowedDepartments", allowedDepartments.stream().map(Enum::name).collect(Collectors.toList()));
        metadata.put("answered", answered);
        metadata.put("confidence", confidence);
        metadata.put("latencyMs", latencyMs);
        metadata.put("citedDocumentIds", citedDocIds);

        auditEventPublisher.publish(AuditAction.QUERY_ASKED, "QUERY", queryId.toString(), metadata);
    }

    private static List<String> splitIntoTokens(String answer) {
        if (answer.isEmpty()) {
            return List.of("");
        }
        List<String> tokens = new ArrayList<>();
        String[] words = answer.split("(?<=\\s)");
        StringBuilder batch = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            batch.append(words[i]);
            if ((i + 1) % 4 == 0 || i == words.length - 1) {
                tokens.add(batch.toString());
                batch.setLength(0);
            }
        }
        return tokens;
    }

    private ChatMessageDto toMessageDto(ChatMessageEntity m) {
        return ChatMessageDto.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .citations(deserializeCitations(m.getCitations()))
                .answered(m.getAnswered())
                .feedback(m.getFeedback())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private String serializeCitations(List<CitationDto> citations) {
        try {
            return objectMapper.writeValueAsString(citations != null ? citations : Collections.emptyList());
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<CitationDto> deserializeCitations(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CitationDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static String truncateTitle(String message) {
        if (message == null || message.isBlank()) {
            return "New Conversation";
        }
        String trimmed = message.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }
}
