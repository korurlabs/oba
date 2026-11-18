package com.korur.oba.connect.notification.config;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

// 2. Third-party
import lombok.Getter;

@Configuration
@Getter
public class EmailConfiguration {

    @Value("${sensey.notification.default-sender}")
    private String defaultSender;

    @Value("${sensey.logo.url}")
    private String senseyLogoUrl;
}
