package backend.knowledge_api_service.controller;

import backend.knowledge_api_service.dto.request.ChatRequest;
import backend.knowledge_api_service.dto.response.ChatResponse;
import backend.knowledge_api_service.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Conversational RAG assistant with grounded source citations")
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
}

