package com.swp.ckms.controller;

import com.swp.ckms.dto.request.CreateUserRequest;
import com.swp.ckms.dto.request.UpdateUserRequest;
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
    @PreAuthorize("hasAuthority('CREATE_USER')")
    public ResponseEntity<CreateUserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_USER')")
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
    @PreAuthorize("hasAuthority('VIEW_USER')")
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE_USER')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request
    ) {

        UserResponse result = userService.updateUser(id, request);

        return ResponseEntity.ok(
                ApiResponse.success("User updated successfully", result)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_USER')")
    public ResponseEntity<ApiResponse<String>> deleteUser(
            @PathVariable Long id
    ) {

        userService.deleteUser(id);

        return ResponseEntity.ok(
                ApiResponse.success("User deleted successfully", null)
        );
    }
}
