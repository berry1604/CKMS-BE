package com.swp.ckms.service.impl;

import com.swp.ckms.config.AhamoveProperties;
import com.swp.ckms.dto.ahamove.AhamoveOrderRequest;
import com.swp.ckms.dto.ahamove.AhamoveOrderResponse;
import com.swp.ckms.dto.ahamove.AhamoveTokenResponse;
import com.swp.ckms.service.AhamoveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AhamoveServiceImpl implements AhamoveService {

    private final AhamoveProperties properties;
    private final WebClient webClient;

    @Override
    public String getAccessToken() {
        try {
            AhamoveTokenResponse response = webClient.post()
                    .uri(properties.getBaseUrl() + "/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "api_key", properties.getKey(),
                            "mobile", properties.getPhone(),
                            "name", properties.getName()
                    ))
                    .retrieve()
                    .bodyToMono(AhamoveTokenResponse.class)
                    .block();

            if (response == null || response.getToken() == null) {
                throw new RuntimeException("Ahamove trả về token rỗng");
            }

            log.info("Lấy Ahamove token thành công cho số điện thoại: {}", properties.getPhone());
            return response.getToken();

        } catch (WebClientResponseException e) {
            log.error("Ahamove đăng nhập thất bại: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Xác thực Ahamove thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public AhamoveOrderResponse createOrder(String token, AhamoveOrderRequest request) {
        try {
            log.info("Tạo đơn Ahamove với service: {}", request.getServiceId());

            AhamoveOrderResponse response = webClient.post()
                    .uri(properties.getBaseUrl() + "/v1/order/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AhamoveOrderResponse.class)
                    .block();

            if (response == null || response.getOrderId() == null) {
                throw new RuntimeException("Ahamove trả về kết quả tạo đơn rỗng");
            }

            log.info("Tạo đơn Ahamove thành công: {} | Tracking: {}",
                    response.getOrderId(), response.getSharedLink());
            return response;

        } catch (WebClientResponseException e) {
            log.error("Tạo đơn Ahamove thất bại: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Tạo đơn Ahamove thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelOrder(String token, String ahamoveOrderId, String comment) {
        try {
            webClient.post()
                    .uri(properties.getBaseUrl() + "/v1/order/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .bodyValue(Map.of(
                            "order_id", ahamoveOrderId,
                            "comment", comment != null ? comment : "Cancelled by system"
                    ))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Đã hủy đơn AhaMove: {}", ahamoveOrderId);

        } catch (WebClientResponseException e) {
            // Chỉ log warning, không throw
            // Lý do: việc hủy nội bộ vẫn phải tiếp tục dù AhaMove fail
            log.warn("Không thể hủy đơn AhaMove {}: {} - {}",
                    ahamoveOrderId, e.getStatusCode(), e.getResponseBodyAsString());
        }
    }

    @Override
    public AhamoveOrderResponse getOrderDetail(String token, String ahamoveOrderId) {
        try {
            return webClient.get()
                    .uri(properties.getBaseUrl() + "/v1/order/detail?id=" + ahamoveOrderId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(AhamoveOrderResponse.class)
                    .block();

        } catch (WebClientResponseException e) {
            log.error("Lấy thông tin đơn AhaMove {} thất bại: {}", ahamoveOrderId, e.getMessage());
            throw new RuntimeException("Lấy thông tin đơn AhaMove thất bại: " + e.getMessage(), e);
        }
    }
}