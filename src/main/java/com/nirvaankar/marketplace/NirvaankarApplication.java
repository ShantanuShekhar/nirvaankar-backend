package com.nirvaankar.marketplace;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.config.S3Properties;
import com.nirvaankar.marketplace.common.config.WhatsAppCloudProperties;
import com.nirvaankar.marketplace.common.config.WhatsAppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties({
        NirvaankarProperties.class,
        S3Properties.class,
        WhatsAppProperties.class,
        WhatsAppCloudProperties.class})
public class NirvaankarApplication {

    public static void main(String[] args) {
        // Every business timestamp in this system is UTC. Pinning the JVM
        // removes an entire class of "works on my machine" date bugs.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(NirvaankarApplication.class, args);
    }
}
