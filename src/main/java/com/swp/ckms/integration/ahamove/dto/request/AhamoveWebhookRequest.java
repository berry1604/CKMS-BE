package com.swp.ckms.integration.ahamove.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
/**
 * AhaMove gửi webhook về endpoint của chúng ta khi trạng thái đơn thay đổi.
 * Các status: ASSIGNING, ACCEPTED, IN PROCESS, COMPLETED, CANCELLED, FAILED
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AhamoveWebhookRequest {

    @JsonProperty("_id")
    private String orderId;

    // Trạng thái mới từ AhaMove
    private String status;

    @JsonProperty("sub_status")
    private String subStatus;

    private List<AhamovePathPoint> path;

    // Thông tin tài xế được gán
    @JsonProperty("supplier_id")
    private String supplierId;

    @JsonProperty("supplier_name")
    private String supplierName;

    @JsonProperty("supplier_mobile")
    private String supplierMobile;

    // Comment khi CANCELLED hoặc FAILED
    private String comment;

    // // Timestamp
    // @JsonProperty("_id")
    // private String requestId;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AhamovePathPoint {
        private String status;
    }
}