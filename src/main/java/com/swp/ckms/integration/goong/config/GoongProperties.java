package com.swp.ckms.integration.goong.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "goong.api")
public class GoongProperties {
	private String baseUrl;
	private String key;
	private Boolean hasDeprecatedAdministrativeUnit = false;
}
