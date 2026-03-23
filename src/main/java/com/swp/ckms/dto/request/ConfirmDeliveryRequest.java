package com.swp.ckms.dto.request;

import lombok.Data;

@Data
public class ConfirmDeliveryRequest {
    private String feedbackNote;       // Phản hồi chất lượng
    private Integer qualityRating;     // 1-5 sao (optional)
}