package com.swp.ckms.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private final Cors cors = new Cors();

    @Data
    public static class Cors {
        private List<String> allowedOrigins;
    }
}
