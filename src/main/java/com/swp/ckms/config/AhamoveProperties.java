package com.swp.ckms.config;

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
    // private String name;
    // private String address;
}