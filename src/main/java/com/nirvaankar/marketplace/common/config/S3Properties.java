package com.nirvaankar.marketplace.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "nirvaankar.s3")
public record S3Properties(
        @DefaultValue("ap-south-1") String region,
        @DefaultValue("nirvaankar") String bucket,
        @DefaultValue("products/") String prefix,
        @DefaultValue("") String accessKey,
        @DefaultValue("") String secretKey,
        @DefaultValue("5242880") long maxFileBytes) {

    public boolean configured() {
        return accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()
                && bucket != null && !bucket.isBlank()
                && region != null && !region.isBlank();
    }

    public String normalizedPrefix() {
        String value = prefix == null || prefix.isBlank() ? "products/" : prefix.trim();
        return value.endsWith("/") ? value : value + "/";
    }
}
