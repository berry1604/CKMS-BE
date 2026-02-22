package com.swp.ckms.service;

import com.swp.ckms.dto.request.LoginRequest;
import com.swp.ckms.dto.response.LoginResponse;
import com.swp.ckms.dto.request.LogoutRequest;

public interface AuthService {
    LoginResponse login(LoginRequest loginRequest);
    void logout(LogoutRequest logoutRequest);
    LoginResponse refreshToken(String requestRefreshToken);
}
