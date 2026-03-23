package com.swp.ckms.service;

import com.swp.ckms.enums.NotificationType;
import java.util.Map;

public interface NotificationService {
    void createEmailNotification(NotificationType type, String recipient, String template, Map<String, Object> payload, String dedupKey);
}
