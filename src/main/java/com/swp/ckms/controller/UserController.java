package com.swp.ckms.controller;

import com.swp.ckms.dto.request.CreateUserRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.CreateUserResponse;
import com.swp.ckms.dto.response.UserResponse;
import com.swp.ckms.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreateUserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {

        Page<UserResponse> result =
                userService.getUsers(page, size, role, status, search);

        return ResponseEntity.ok(
                ApiResponse.success("Users fetched successfully", result)
        );
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByUsernameOrEmail(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email
    ) {

        UserResponse result =
                userService.getUserByUsernameOrEmail(username, email);

        return ResponseEntity.ok(
                ApiResponse.success("User fetched successfully", result)
        );
    }
}
