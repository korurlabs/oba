package com.korur.oba.connect.intelligence.core.config;

// 1. Framework-level
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.korur.oba.connect.intelligence.core.properties.IntelligenceRoutingProperties;
import com.korur.oba.connect.intelligence.gemini.properties.GeminiProperties;
import com.korur.oba.connect.intelligence.grok.properties.GrokProperties;

/**
 * Central configuration for the intelligence layer.
 * Creates property beans for routing and provider-specific settings.
 */
@Configuration
public class IntelligenceConfig {

    @Bean
    @ConfigurationProperties(prefix = "intelligence.routing")
    public IntelligenceRoutingProperties intelligenceRoutingProperties() {
        return new IntelligenceRoutingProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "intelligence.gemini")
    public GeminiProperties geminiProperties() {
        return new GeminiProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "intelligence.grok")
    public GrokProperties grokProperties() {
        return new GrokProperties();
    }
}
