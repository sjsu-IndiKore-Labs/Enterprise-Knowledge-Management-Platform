package backend.knowledge_api_service.service;

import backend.knowledge_api_service.dto.request.ChatRequest;
import backend.knowledge_api_service.dto.response.*;
import backend.knowledge_api_service.entity.ChatMessageEntity;
import backend.knowledge_api_service.entity.Conversation;
import backend.knowledge_api_service.exception.ResourceNotFoundException;
import backend.knowledge_api_service.filter.RequestIdFilter;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.MessageRole;
import backend.knowledge_api_service.repository.ChatMessageRepository;
import backend.knowledge_api_service.repository.ConversationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChatResponse chat(ChatRequest request) {
        RequestIdFilter.CallerInfo caller = RequestIdFilter.getCurrentCaller();
        UUID userId = caller.userId();

        // 1. Load or create Conversation
        Conversation conversation;
        if (request.getConversationId() != null) {
            conversation = conversationRepository.findByIdAndUserId(request.getConversationId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("CONVERSATION_NOT_FOUND",
                            "Conversation not found with ID: " + request.getConversationId()));
        } else {
            String title = truncateTitle(request.getMessage());
            conversation = Conversation.builder()
                    .userId(userId)
                    .title(title)
                    .build();
            conversation = conversationRepository.save(conversation);
        }

        // 2. Fetch last 6 messages as history (in chronological order) before adding the new USER message
        List<ChatMessageEntity> recentDesc = chatMessageRepository
                .findTop6ByConversationIdOrderByCreatedAtDesc(conversation.getId());
        List<ChatMessageEntity> recentChronological = new ArrayList<>(recentDesc);
        Collections.reverse(recentChronological);

        List<AiServiceClient.HistoryMessage> history = recentChronological.stream()
                .map(m -> AiServiceClient.HistoryMessage.builder()
                        .role(m.getRole().name())
                        .content(m.getContent())
                        .build())
                .collect(Collectors.toList());

        // 3. Persist USER message
        ChatMessageEntity userMsg = ChatMessageEntity.builder()
                .conversationId(conversation.getId())
                .role(MessageRole.USER)
                .content(request.getMessage())
                .citations("[]")
                .build();
        chatMessageRepository.save(userMsg);

        // 4. Compute effective allowedDepartments (intersect request.departments with caller's allowedDepartments)
        List<Department> callerAllowed = caller.allowedDepartments();
        List<Department> effectiveDepartments;
        if (request.getDepartments() != null && !request.getDepartments().isEmpty()) {
            effectiveDepartments = request.getDepartments().stream()
                    .filter(callerAllowed::contains)
                    .distinct()
                    .collect(Collectors.toList());
        } else {
            effectiveDepartments = new ArrayList<>(callerAllowed);
        }

        UUID queryId = UUID.randomUUID();
        AiServiceClient.InternalAnswerRequest answerRequest = AiServiceClient.InternalAnswerRequest.builder()
                .queryId(queryId)
                .query(request.getMessage())
                .allowedDepartments(effectiveDepartments)
                .history(history)
                .topK(6)
                .build();

        // 5. Call ai-service /internal/v1/answer
        AiServiceClient.InternalAnswerResponse aiAnswer = aiServiceClient.answer(answerRequest);
        List<CitationDto> citations = aiAnswer.getCitations() != null
                ? aiAnswer.getCitations()
                : Collections.emptyList();

        // 6. Persist ASSISTANT message with citations JSON
        String citationsJson = serializeCitations(citations);
        ChatMessageEntity assistantMsg = ChatMessageEntity.builder()
                .conversationId(conversation.getId())
                .role(MessageRole.ASSISTANT)
                .content(aiAnswer.getAnswer() != null ? aiAnswer.getAnswer() : "")
                .citations(citationsJson)
                .queryId(queryId)
                .answered(aiAnswer.getAnswered() != null ? aiAnswer.getAnswered() : false)
                .build();
        ChatMessageEntity savedAssistantMsg = chatMessageRepository.save(assistantMsg);

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        return ChatResponse.builder()
                .queryId(queryId)
                .conversationId(conversation.getId())
                .messageId(savedAssistantMsg.getId())
                .answer(savedAssistantMsg.getContent())
                .answered(savedAssistantMsg.getAnswered())
                .confidence(aiAnswer.getConfidence() != null ? aiAnswer.getConfidence() : 0.0)
                .citations(citations)
                .latencyMs(aiAnswer.getLatencyMs() != null ? aiAnswer.getLatencyMs() : 0)
                .build();
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

