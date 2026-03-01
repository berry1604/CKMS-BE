package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.*;
import com.swp.ckms.dto.response.CreateUserResponse;
import com.swp.ckms.dto.response.UserResponse;
import com.swp.ckms.entity.User;
import com.swp.ckms.enums.UserStatus;
import com.swp.ckms.event.UserCreatedEvent;
import com.swp.ckms.entity.Role;
import com.swp.ckms.repository.RoleRepository;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.service.EmailService;
import com.swp.ckms.service.UserService;
import com.swp.ckms.specification.UserSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend.reset-password-url:http://localhost:5173/reset-password}")
    private String resetPasswordUrl;

    @Override
    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        String username = generateUniqueUsername(request.getEmail());
        String rawToken = generateSecureToken();
        // log.info("Generated raw token for {}: {}", username, rawToken);
        String tokenHash = hashToken(rawToken);
        // log.info("Generated hash for {}: {}", username, tokenHash);

        // Fetch Role
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));

        User user = User.builder()
                .username(username)
                .email(request.getEmail())
                .fullName(request.getFullName())
                .status(UserStatus.PENDING_VERIFICATION)
                .password(null) // explicit null, though default is null
                .verificationTokenHash(tokenHash)
                .verificationTokenExpiresAt(LocalDateTime.now().plusHours(24))
                .isActive(true)
                .role(role)
                // store and kitchen handling omitted for brevity as per plan default
                .build();

        User savedUser = userRepository.save(user);

        eventPublisher.publishEvent(new UserCreatedEvent(this, savedUser, rawToken));

        return CreateUserResponse.builder()
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .token(rawToken) // Expose token for testing/dev
                .message("User created successfully. Please check email or use this token to activate.")
                .build();
    }

    private String generateUniqueUsername(String email) {
        String prefix = email.split("@")[0];
        // simple unique check loop could be added here, but for now just append random suffix
        String randomSuffix = UUID.randomUUID().toString().substring(0, 5); // 5 chars
        String candidate = prefix + "." + randomSuffix;
        
        while (userRepository.existsByUsername(candidate)) {
             randomSuffix = UUID.randomUUID().toString().substring(0, 5);
             candidate = prefix + "." + randomSuffix;
        }
        return candidate;
    }

    private String generateSecureToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }

    @Override
    @Transactional
    public void activateAccount(ActivateAccountRequest request) {
        // ... (existing code)
        // log.info("Request to activate account with token: {}", request.getToken());
        String tokenHash = hashToken(request.getToken());
        // log.info("Computed hash from request token: {}", tokenHash);
        
        User user = userRepository.findByVerificationTokenHash(tokenHash)
                .orElseThrow(() -> {
                    // log.error("Token hash not found in DB: {}", tokenHash);
                    return new RuntimeException("Invalid or expired activation token");
                });

        if (user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Activation token has expired");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        user.setIsActive(true);
        user.setVerificationTokenHash(null);
        user.setVerificationTokenExpiresAt(null);

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + request.getEmail()));

        // Check if user is active or pending (not banned/deleted)
        if (user.getStatus() == UserStatus.INACTIVE) {
             throw new RuntimeException("Account is inactive");
        }

        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        user.setVerificationTokenHash(tokenHash);
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusMinutes(15)); // Short expiry for reset

        userRepository.save(user);

        String resetLink = resetPasswordUrl + "?token=" + rawToken; 
        
        emailService.sendResetPasswordEmail(user.getEmail(), user.getFullName(), resetLink);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = hashToken(request.getToken());
        
        User user = userRepository.findByVerificationTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
            user.setIsActive(true);
        }

        user.setVerificationTokenHash(null);
        user.setVerificationTokenExpiresAt(null);

        userRepository.save(user);
    }

    @Override
    public Page<UserResponse> getUsers(
            int page,
            int size,
            String role,
            String status,
            String search
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("userId").descending()
        );

        Specification<User> specification =
                UserSpecification.filterUsers(role, status, search);

        Page<User> users =
                userRepository.findAll(specification, pageable);

        return users.map(this::mapToResponse);
    }

    @Override
    public UserResponse getUserByUsernameOrEmail(String username, String email) {

        boolean hasUsername = username != null && !username.isBlank();
        boolean hasEmail = email != null && !email.isBlank();

        if (hasUsername == hasEmail) {
            throw new IllegalArgumentException(
                    "Provide either username or email"
            );
        }

        User user;

        if (hasUsername) {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() ->
                            new RuntimeException("User not found"));
        } else {
            user = userRepository.findByEmail(email)
                    .orElseThrow(() ->
                            new RuntimeException("User not found"));
        }

        return mapToResponse(user);
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole() != null
                        ? user.getRole().getRoleName()
                        : null)
                .status(user.getStatus().name())
                .isActive(user.getIsActive())
                .build();
    }

    @Override
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getRoleId() != null) {
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Role not found"));
            user.setRole(role);
        }

        if (request.getStatus() != null) {
            user.setStatus(UserStatus.valueOf(request.getStatus()));
        }

        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }

        userRepository.save(user);

        return mapToResponse(user);
    }
}
