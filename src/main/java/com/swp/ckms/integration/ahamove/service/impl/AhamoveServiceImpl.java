package com.swp.ckms.integration.ahamove.service.impl;

import com.swp.ckms.integration.ahamove.config.AhamoveProperties;
import com.swp.ckms.integration.ahamove.dto.request.AhamoveOrderRequest;
import com.swp.ckms.integration.ahamove.dto.response.AhamoveOrderResponse;
import com.swp.ckms.integration.ahamove.dto.response.AhamoveTokenResponse;
import com.swp.ckms.integration.ahamove.service.AhamoveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AhamoveServiceImpl implements AhamoveService {

    private final AhamoveProperties properties;
    private final WebClient webClient;

    @Override
    public String getAccessToken() {
        String loginUrl = properties.getBaseUrl() + "/v3/accounts/token";
        log.info("Goi AhaMove lay token tai: {}", loginUrl);

        try {
            // === BUOC 1: Nhan raw String de xem chinh xac JSON tra ve ===
            String rawResponse = webClient.post()
                    .uri(loginUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "mobile",  properties.getPhone(),
                            "api_key", properties.getKey()
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("AhaMove token raw response: {}", rawResponse);

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new RuntimeException("Ahamove tra ve response rong");
            }

            // === BUOC 2: Parse thu cong de lay token ===
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(rawResponse);

            // Thu lay tu tat ca field co the co
            String token = null;
            if (root.has("token") && !root.get("token").isNull()) {
                token = root.get("token").asText();
            }
            if ((token == null || token.isBlank()) && root.has("_id") && !root.get("_id").isNull()) {
                token = root.get("_id").asText();
            }
            if ((token == null || token.isBlank()) && root.has("access_token") && !root.get("access_token").isNull()) {
                token = root.get("access_token").asText();
            }

            log.info("Parsed token: {}", token != null ? token.substring(0, Math.min(30, token.length())) + "..." : "NULL");

            if (token == null || token.isBlank() || "null".equals(token)) {
                throw new RuntimeException("Ahamove tra ve token rong. Raw: " + rawResponse);
            }

            return token;

        } catch (WebClientResponseException e) {
            log.error("Ahamove lay token that bai: {} - Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Xac thuc Ahamove that bai: " + e.getResponseBodyAsString(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Loi xu ly AhaMove response: {}", e.getMessage());
            throw new RuntimeException("Loi xu ly response AhaMove", e);
        }
    }

    @Override
    public AhamoveOrderResponse createOrder(String token, AhamoveOrderRequest request) {
        String createUrl = properties.getBaseUrl() + "/v3/orders";

        try {
            ObjectMapper mapper = new ObjectMapper();
            String requestBody = mapper.writeValueAsString(request);
            log.info("Tao don Ahamove tai: {}", createUrl);
            log.info("Request body: {}", requestBody);

            String rawResponse = webClient.post()
                    .uri(createUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("AhaMove create order raw response: {}", rawResponse);

            AhamoveOrderResponse response = mapper.readValue(rawResponse, AhamoveOrderResponse.class);

            if (response == null || response.getOrderId() == null) {
                throw new RuntimeException("Ahamove tra ve ket qua tao don rong. Raw: " + rawResponse);
            }

            log.info("Tao don Ahamove thanh cong: {} | Tracking: {}",
                    response.getOrderId(), response.getSharedLink());
            return response;

        } catch (WebClientResponseException e) {
            log.error("Tao don Ahamove that bai: {} - {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Tao don Ahamove that bai: " + e.getResponseBodyAsString(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Loi xu ly tao don Ahamove: {}", e.getMessage());
            throw new RuntimeException("Loi tao don Ahamove", e);
        }
    }

    @Override
    public void cancelOrder(String token, String ahamoveOrderId, String comment) {
        try {
            webClient.post()
                    .uri(properties.getBaseUrl() + "/v3/orders/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .bodyValue(Map.of(
                            "order_id", ahamoveOrderId,
                            "comment",  comment != null ? comment : "Cancelled by system"
                    ))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Da huy don AhaMove: {}", ahamoveOrderId);

        } catch (WebClientResponseException e) {
            log.warn("Khong the huy don AhaMove {}: {} - {}",
                    ahamoveOrderId, e.getStatusCode(), e.getResponseBodyAsString());
        }
    }

    @Override
    public AhamoveOrderResponse getOrderDetail(String token, String ahamoveOrderId) {
        try {
            return webClient.get()
                    .uri(properties.getBaseUrl() + "/v3/orders/" + ahamoveOrderId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(AhamoveOrderResponse.class)
                    .block();

        } catch (WebClientResponseException e) {
            log.error("Lay thong tin don AhaMove {} that bai: {}", ahamoveOrderId, e.getMessage());
            throw new RuntimeException("Lay thong tin don AhaMove that bai: " + e.getMessage(), e);
        }
    }
}