package com.swp.ckms.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swp.ckms.entity.NotificationJob;
import com.swp.ckms.enums.NotificationStatus;
import com.swp.ckms.enums.NotificationType;
import com.swp.ckms.repository.NotificationJobRepository;
import com.swp.ckms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationJobRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void createEmailNotification(NotificationType type, String recipient, String template, Map<String, Object> payload, String dedupKey) {
        
        if (dedupKey != null && repository.existsByDeduplicationKey(dedupKey)) {
            log.info("Duplicate notification detected for key: {}. Skipping.", dedupKey);
            return;
        }

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            NotificationJob job = NotificationJob.builder()
                    .eventType(type)
                    .templateName(template)
                    .recipientEmail(recipient)
                    .payloadJson(payloadJson)
                    .deduplicationKey(dedupKey)
                    .status(NotificationStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            if (job == null) {
                log.error("Failed to persist notification job for {}", recipient);
                return;
            }
            repository.save(job);
            log.debug("Created notification job for recipient: {} with type: {}", recipient, type);
            
        } catch (Exception e) {
            log.error("Failed to create notification job for recipient: {}", recipient, e);
        }
    }
}
