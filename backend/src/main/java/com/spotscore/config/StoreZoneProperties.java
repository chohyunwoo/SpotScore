package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spotscore.store-zone")
public record StoreZoneProperties(String baseUrl, String serviceKey) {
}
