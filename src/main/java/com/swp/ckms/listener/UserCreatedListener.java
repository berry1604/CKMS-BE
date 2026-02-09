package com.swp.ckms.listener;

import com.swp.ckms.event.UserCreatedEvent;
import com.swp.ckms.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCreatedListener {

    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserCreatedEvent(UserCreatedEvent event) {
        log.info("Handling UserCreatedEvent for user: {}", event.getUser().getUsername());
        
        String verificationLink = baseUrl + "/api/v1/auth/verify?userId=" + event.getUser().getUserId() + "&token=" + event.getRawToken();
        
        emailService.sendVerificationEmail(
                event.getUser().getEmail(), 
                event.getUser().getFullName(),
                event.getUser().getUsername(),
                verificationLink
        );
    }
}
