package com.swp.ckms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.swp.ckms.config.AppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
@EnableConfigurationProperties(AppProperties.class)
public class CkmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CkmsApplication.class, args);
    }

}
