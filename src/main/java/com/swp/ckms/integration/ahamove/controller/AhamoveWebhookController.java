package com.swp.ckms.integration.ahamove.controller;

import com.swp.ckms.integration.ahamove.dto.request.AhamoveWebhookRequest;
import com.swp.ckms.integration.ahamove.shipment.AhamoveShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/webhooks/ahamove")
@RequiredArgsConstructor
@Slf4j
public class AhamoveWebhookController {

    private final AhamoveShipmentService ahamoveShipmentService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody AhamoveWebhookRequest request) {

        try {
            // Requirement: Log toàn bộ payload nhận được
            String rawJson = objectMapper.writeValueAsString(request);
            log.info("=== NHẬN WEBHOOK TỪ AHAMOVE ===");
            log.info("Raw Payload: {}", rawJson);
            
            // Parse specific requested fields for logging
            String orderId = request.getOrderId();
            String status = request.getStatus();
            String driverName = request.getSupplierName();
            String driverPhone = request.getSupplierMobile();
            
            log.info("Parsed -> orderId: {}, status: {}, driverName: {}, driverPhone: {}", 
                     orderId, status, driverName, driverPhone);
                     
            if (orderId == null) {
                log.warn("Thiếu order_id trong payload, bỏ qua.");
                return ResponseEntity.ok(Map.of("message", "ignored", "reason", "missing order_id"));
            }

            boolean updated = ahamoveShipmentService.handleWebhookUpdate(request);

            if (updated) {
                 log.info("Cập nhật Backend DB thành công cho Ahamove Order ID: {}", orderId);
            }

            return ResponseEntity.ok(Map.of(
                    "message", updated ? "ok" : "ignored"
            ));
            
        } catch (Exception e) {
            log.error("=== LỖI KHI XỬ LÝ WEBHOOK AHAMOVE ===", e);
            // Requirement: Không crash, return 200/500 tuỳ ý nhưng catch lỗi
            return ResponseEntity.ok(Map.of("message", "error", "reason", e.getMessage()));
        }
    }
}