package com.swp.ckms.controller;

import com.swp.ckms.dto.request.ActivateAccountRequest;
import com.swp.ckms.dto.request.ForgotPasswordRequest;
import com.swp.ckms.dto.request.LoginRequest;
import com.swp.ckms.dto.request.ResetPasswordRequest;
import com.swp.ckms.dto.response.LoginResponse;
import com.swp.ckms.service.AuthService;
import com.swp.ckms.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody String refreshToken) {
         return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    @PostMapping("/activate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    public ResponseEntity<String> activateAccount(@RequestBody ActivateAccountRequest request) {
        userService.activateAccount(request);
        return ResponseEntity.ok("Account activated successfully");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request);
        return ResponseEntity.ok("Reset password email sent");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ResponseEntity.ok("Password reset successfully");
    }
}
