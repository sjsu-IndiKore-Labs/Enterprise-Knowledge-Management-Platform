package backend.knowledge_api_service.controller;

import backend.knowledge_api_service.dto.request.CreateUserRequest;
import backend.knowledge_api_service.dto.request.UpdateUserRequest;
import backend.knowledge_api_service.dto.response.UserDto;
import backend.knowledge_api_service.dto.response.UserPageResponse;
import backend.knowledge_api_service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users", description = "Enterprise user administration (ADMIN only)")
public class AdminUserController {

    private final AuthService authService;

    @GetMapping
    @Operation(summary = "List users (ADMIN only)")
    public ResponseEntity<UserPageResponse> listUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(authService.listUsers(page, size));
    }

    @PostMapping
    @Operation(summary = "Create a user (ADMIN only)")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createUser(request));
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Change role / department / active flag (ADMIN only)")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(authService.updateUser(userId, request));
    }
}

