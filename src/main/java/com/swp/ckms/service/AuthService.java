package com.swp.ckms.service;

import com.swp.ckms.dto.request.LoginRequest;
import com.swp.ckms.dto.response.LoginResponse;
import com.swp.ckms.dto.request.LogoutRequest;
import com.swp.ckms.entity.RefreshToken;
import com.swp.ckms.entity.User;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.security.JwtTokenProvider;
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
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

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
        
        String jwt = tokenProvider.generateToken(user.getUsername(), user.getUserId(), user.getRole().getRoleName());
        
        // Create Refresh Token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUserId());

        return LoginResponse.builder()
                .accessToken(jwt)
                .refreshToken(refreshToken.getToken())
                .accessTokenExpiresIn(jwtExpiration / 1000)
                .build();
    }

    public void logout(LogoutRequest logoutRequest) {
        refreshTokenService.deleteByToken(logoutRequest.getRefreshToken());
    }

    public LoginResponse refreshToken(String refreshToken) {
        return refreshTokenService.findByToken(refreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String accessToken = tokenProvider.generateToken(user.getUsername(), user.getUserId(), user.getRole().getRoleName());
                    return LoginResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .accessTokenExpiresIn(jwtExpiration / 1000)
                            .build();
                })
                .orElseThrow(() -> new com.swp.ckms.exception.auth.RefreshTokenExpiredException(
                        "Refresh token is not in database!"));
    }
}
