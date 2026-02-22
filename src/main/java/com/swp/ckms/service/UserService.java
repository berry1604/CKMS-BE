package com.swp.ckms.service;

import com.swp.ckms.dto.request.CreateUserRequest;
import com.swp.ckms.dto.response.CreateUserResponse;
import com.swp.ckms.dto.request.ForgotPasswordRequest;
import com.swp.ckms.dto.request.ResetPasswordRequest;
import com.swp.ckms.dto.request.ActivateAccountRequest;

public interface UserService {
    CreateUserResponse createUser(CreateUserRequest request);
    void activateAccount(ActivateAccountRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
}
