package com.swp.ckms.service;

import com.swp.ckms.entity.NotificationJob;

public interface EmailTemplateService {
    String renderHtml(NotificationJob job);
    String resolveSubject(NotificationJob job);
}
