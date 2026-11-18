package com.korur.oba.connect.intelligence.core.service;

// 1. Framework-level
import org.springframework.core.ParameterizedTypeReference;

import com.korur.oba.connect.intelligence.core.enums.IntelligenceProvider;
import com.korur.oba.connect.intelligence.core.enums.IntelligenceUseCase;

/**
 * Multi-model AI abstraction layer.
 *
 * Domain code calls this interface without knowing which provider handles
 * the request. The intelligence router resolves use-cases to providers.
 */
public interface IntelligenceService {

    /**
     * Checks if a specific AI provider is configured and available.
     */
    boolean isAvailable(IntelligenceProvider provider);

    /**
     * Sends a chat request routed by use-case.
     * The router resolves the use-case to the configured provider.
     */
    String chat(IntelligenceUseCase useCase, String systemPrompt, String userMessage);

    /**
     * Sends a chat request to a specific provider (explicit override).
     */
    String chat(IntelligenceProvider provider, String systemPrompt, String userMessage);

    /**
     * Sends a multimodal chat request with an image attachment, routed by use-case.
     */
    String chatWithImage(IntelligenceUseCase useCase, String systemPrompt, String userMessage,
                         byte[] imageBytes, String mimeType);

    /**
     * Sends a multimodal chat request with a file attachment and asks Spring AI to convert the
     * provider response into a typed object using the given {@link ParameterizedTypeReference}.
     *
     * The schema for the target type is auto-injected into the prompt by Spring AI, and the
     * response JSON is parsed via the configured output converter.
     *
     * @param useCase      use-case used to resolve the provider via the router
     * @param model        optional runtime model override (e.g. "claude-3-5-haiku-20241022").
     *                     When null/blank the provider's default model from application.properties is used.
     * @param systemPrompt system prompt that frames the task
     * @param userMessage  user message describing the task and instructions
     * @param bytes        raw bytes of the attached file (image or PDF)
     * @param mimeType     IANA mime type of the attached file
     * @param type         Spring {@link ParameterizedTypeReference} of the desired return type
     * @param <T>          desired return type
     * @return parsed response as an instance of T
     */
    <T> T chatWithImageEntity(IntelligenceUseCase useCase, String model, String systemPrompt, String userMessage,
                              byte[] bytes, String mimeType, ParameterizedTypeReference<T> type);
}
