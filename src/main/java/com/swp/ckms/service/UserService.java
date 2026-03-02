package com.swp.ckms.service;

import com.swp.ckms.dto.request.*;
import com.swp.ckms.dto.response.CreateUserResponse;
import com.swp.ckms.dto.response.UserResponse;
import org.springframework.data.domain.Page;

public interface UserService {
    CreateUserResponse createUser(CreateUserRequest request);
    void activateAccount(ActivateAccountRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);

    Page<UserResponse> getUsers(
            int page,
            int size,
            String role,
            String status,
            String search
    );

    UserResponse getUserByUsernameOrEmail(String username, String email);

    UserResponse updateUser(Long userId, UpdateUserRequest request);

    void deleteUser(Long userId);
}
