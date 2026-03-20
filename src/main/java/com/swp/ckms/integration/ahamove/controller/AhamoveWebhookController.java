package com.swp.ckms.integration.ahamove.controller;

import com.swp.ckms.integration.ahamove.dto.request.AhamoveWebhookRequest;
import com.swp.ckms.integration.ahamove.shipment.AhamoveShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks/ahamove")
@RequiredArgsConstructor
@Slf4j
public class AhamoveWebhookController {

    private final AhamoveShipmentService ahamoveShipmentService;

    @PostMapping
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody AhamoveWebhookRequest request) {

        log.info("Nhận webhook AhaMove: orderId={}, status={}, supplier={}",
                request.getOrderId(), request.getStatus(), request.getSupplierName());

        boolean updated = ahamoveShipmentService.handleWebhookUpdate(request);

        return ResponseEntity.ok(Map.of(
                "message", updated ? "ok" : "ignored"
        ));
    }
}