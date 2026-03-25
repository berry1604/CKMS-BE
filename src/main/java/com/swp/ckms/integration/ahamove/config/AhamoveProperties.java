package com.swp.ckms.integration.ahamove.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ahamove.api")
public class AhamoveProperties {
    private String baseUrl;
    private String key;
    private String phone;
    private String callbackUrl;
}