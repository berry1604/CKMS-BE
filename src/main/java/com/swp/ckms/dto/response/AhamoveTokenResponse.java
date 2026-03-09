package com.swp.ckms.dto.ahamove;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AhamoveTokenResponse {

    @JsonProperty("_id")
    private String token;

    private String name;
    private String mobile;
}