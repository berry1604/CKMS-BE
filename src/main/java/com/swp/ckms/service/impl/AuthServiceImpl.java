package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.LoginRequest;
import com.swp.ckms.dto.response.LoginResponse;
import com.swp.ckms.dto.request.LogoutRequest;
import com.swp.ckms.entity.RefreshToken;
import com.swp.ckms.entity.User;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.security.JwtTokenProvider;
import com.swp.ckms.service.AuthService;
import com.swp.ckms.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.accessExpiration}")
    private long accessExpirationMs;

    @Value("${jwt.refreshExpiration}")
    private long refreshExpirationMs;

    @jakarta.annotation.PostConstruct
    public void validateConfig() {
        if (accessExpirationMs <= 0 || refreshExpirationMs <= 0) {
            throw new IllegalStateException("JWT expiration config must be > 0");
        }
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String jwt = buildAccessToken(user);

        // Create Refresh Token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUserId());

        return LoginResponse.builder()
                .accessToken(jwt)
                .refreshToken(refreshToken.getToken())
                .accessTokenExpiresIn(accessExpirationMs / 1000)
                .refreshTokenExpiresIn(java.time.Duration.between(java.time.Instant.now(), refreshToken.getExpiryDate()).getSeconds())
                .userId(user.getUserId())
                .build();
    }

    @Override
    public void logout(LogoutRequest logoutRequest) {
        refreshTokenService.deleteByToken(logoutRequest.getRefreshToken());
    }

    @Override
    public LoginResponse refreshToken(String requestRefreshToken) {
        RefreshToken token = refreshTokenService.findByToken(requestRefreshToken)
                .orElseThrow(() -> new com.swp.ckms.exception.auth.RefreshTokenExpiredException("Refresh token is not in database!"));

        refreshTokenService.verifyExpiration(token);

        User user = token.getUser();
        String accessToken = buildAccessToken(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(requestRefreshToken)
                .accessTokenExpiresIn(accessExpirationMs / 1000)
                .refreshTokenExpiresIn(java.time.Duration.between(java.time.Instant.now(), token.getExpiryDate()).getSeconds())
                .userId(user.getUserId())
                .build();
    }

    private String buildAccessToken(User user) {
        Long storeId = user.getStore() != null ? user.getStore().getStoreId() : null;
        Long coordinatorId = user.getKitchen() != null ? user.getKitchen().getKitchenId() : null;
        String scope = resolveScope(user.getRole().getRoleName());

        return tokenProvider.generateToken(
                user.getUsername(),
                user.getUserId(),
                user.getRole().getRoleName(),
                storeId,
                coordinatorId,
                scope
        );
    }

    private String resolveScope(String roleName) {
        if (roleName == null) return "USER";
        return switch (roleName.toUpperCase()) {
            case "ADMIN", "COORDINATOR" -> "SYSTEM";
            case "MANAGER" -> "SYSTEM"; // Manager có thể xem báo cáo tổng hợp
            case "STORE_STAFF" -> "STORE";
            case "KITCHEN_STAFF" -> "KITCHEN";
            default -> "USER";
        };
    }
}

