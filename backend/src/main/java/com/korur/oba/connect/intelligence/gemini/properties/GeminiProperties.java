package com.korur.oba.connect.intelligence.gemini.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Properties class for Google Gemini API configuration.
 * Maps properties from application.properties with prefix "intelligence.gemini".
 */
@Setter
@Getter
public class GeminiProperties {
    private String apiKey;
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/";
    private String model = "gemini-2.5-flash";
    private int maxTokens = 8192;
}
