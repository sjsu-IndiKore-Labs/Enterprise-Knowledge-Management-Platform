package backend.knowledge_api_service.controller;

import backend.knowledge_api_service.dto.request.LoginRequest;
import backend.knowledge_api_service.dto.response.CurrentUserDto;
import backend.knowledge_api_service.dto.response.LoginResponse;
import backend.knowledge_api_service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Auth & Users", description = "JWT authentication and current user profile")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/login")
    @Operation(summary = "Log in with email + password and receive a JWT")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/users/me")
    @Operation(summary = "Current user profile and effective permissions")
    public ResponseEntity<CurrentUserDto> getCurrentUser() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }
}

