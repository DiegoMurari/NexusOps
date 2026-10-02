package com.nexusops.iam.controller;

import com.nexusops.iam.dto.AuthRequest;
import com.nexusops.iam.dto.AuthResponse;
import com.nexusops.iam.dto.RefreshTokenRequest;
import com.nexusops.iam.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll() {
        // Get current user from security context
        String username = com.nexusops.shared.security.SecurityUtils.getCurrentUsername()
            .orElseThrow(() -> new IllegalStateException("No authenticated user"));
        authService.logoutAll(username);
        return ResponseEntity.noContent().build();
    }
}