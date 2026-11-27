package backend.knowledge_api_service.controller;

import backend.knowledge_api_service.dto.response.ConversationDetailResponse;
import backend.knowledge_api_service.dto.response.ConversationPageResponse;
import backend.knowledge_api_service.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Conversation history management")
public class ConversationController {

    private final ChatService chatService;

    @GetMapping
    @Operation(summary = "Caller's own conversations (newest first)")
    public ResponseEntity<ConversationPageResponse> listConversations(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(chatService.listConversations(page, size));
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "All messages of one of the caller's conversations")
    public ResponseEntity<ConversationDetailResponse> getConversation(
            @PathVariable("conversationId") UUID conversationId) {
        return ResponseEntity.ok(chatService.getConversation(conversationId));
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "Delete one of the caller's conversations")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable("conversationId") UUID conversationId) {
        chatService.deleteConversation(conversationId);
        return ResponseEntity.noContent().build();
    }
}

