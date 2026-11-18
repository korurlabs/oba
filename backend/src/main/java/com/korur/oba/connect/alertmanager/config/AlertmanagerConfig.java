package com.korur.oba.connect.alertmanager.config;

// 1. Framework-level
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.alertmanager.properties.AlertmanagerProperties;

/**
 * Configuration of the Alertmanager webhook.
 */
@Configuration
public class AlertmanagerConfig {

    /**
     * Creates the Alertmanager webhook properties bean.
     *
     * @return the bound properties
     */
    @Bean
    @ConfigurationProperties(prefix = "oba.integrations.alertmanager")
    public AlertmanagerProperties alertmanagerProperties() {
        return new AlertmanagerProperties();
    }
}
