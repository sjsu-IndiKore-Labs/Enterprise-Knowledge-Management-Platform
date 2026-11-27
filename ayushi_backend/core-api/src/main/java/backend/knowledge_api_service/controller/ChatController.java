package backend.knowledge_api_service.controller;

import backend.knowledge_api_service.dto.request.ChatRequest;
import backend.knowledge_api_service.dto.request.FeedbackRequest;
import backend.knowledge_api_service.dto.response.ChatResponse;
import backend.knowledge_api_service.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Conversational RAG assistant with grounded source citations and SSE streaming")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(
        summary = "Ask the assistant a question (non-streaming)",
        description = "Creates or continues a conversation, sends the last 6 messages as history to ai-service, and persists USER + ASSISTANT messages with citations."
    )
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(request));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Ask the assistant a question, answer streamed as Server-Sent Events",
        description = "Streams meta -> token* -> citations -> done | error events as defined in contracts/streaming.md."
    )
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        return chatService.streamChat(request);
    }

    @PostMapping("/messages/{messageId}/feedback")
    @Operation(summary = "Thumbs up/down on an assistant answer (feeds analytics + RAG evaluation)")
    public ResponseEntity<Void> submitFeedback(
            @PathVariable("messageId") UUID messageId,
            @Valid @RequestBody FeedbackRequest request) {
        chatService.submitFeedback(messageId, request);
        return ResponseEntity.noContent().build();
    }
}
