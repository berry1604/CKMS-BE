package com.swp.ckms.integration.ahamove.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AhamoveTokenResponse {

    @JsonProperty("_id")
    private String token;

    private String name;
    private String mobile;
}