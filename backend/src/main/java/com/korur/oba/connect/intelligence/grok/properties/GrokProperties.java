package com.korur.oba.connect.intelligence.grok.properties;

// 2. Third-party
import lombok.Getter;
import lombok.Setter;

/**
 * Properties class for Grok (xAI) API configuration.
 * Maps properties from application.properties with prefix "intelligence.grok".
 */
@Setter
@Getter
public class GrokProperties {
    private String apiKey;
    private String baseUrl = "https://api.x.ai/v1";
    private String model = "grok-2";
    private int maxTokens = 2048;
}
