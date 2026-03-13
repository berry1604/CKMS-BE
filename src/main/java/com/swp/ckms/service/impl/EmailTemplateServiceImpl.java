package com.swp.ckms.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swp.ckms.entity.NotificationJob;
import com.swp.ckms.service.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateServiceImpl implements EmailTemplateService {

    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    @Override
    public String renderHtml(NotificationJob job) {
        try {
            Context context = new Context();
            Map<String, Object> payload = objectMapper.readValue(job.getPayloadJson(), new TypeReference<Map<String, Object>>() {});
            context.setVariables(payload);
            
            return templateEngine.process("email/" + job.getTemplateName(), context);
        } catch (Exception e) {
            log.error("Failed to render HTML for job: {}", job.getId(), e);
            return "Error rendering email content.";
        }
    }

    @Override
    public String resolveSubject(NotificationJob job) {
        return switch (job.getEventType()) {
            case ORDER_SUBMITTED -> "[CKMS] Đơn hàng mới đã được gửi";
            case ORDER_STATUS_CHANGED -> "[CKMS] Cập nhật trạng thái đơn hàng";
            case SHIPMENT_STARTED -> "[CKMS] Thông báo: Hàng đang trên đường giao";
            case BILLING_STATEMENT_CREATED -> "[CKMS] Thông báo hóa đơn thanh toán tháng";
            default -> "[CKMS] Thông báo hệ thống";
        };
    }
}
