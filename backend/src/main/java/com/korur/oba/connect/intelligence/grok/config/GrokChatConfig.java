package com.korur.oba.connect.intelligence.grok.config;

// 1. Framework-level
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.korur.oba.connect.intelligence.grok.properties.GrokProperties;

// 2. Third-party
import lombok.RequiredArgsConstructor;

/**
 * Creates a named ChatClient for Grok (xAI) using OpenAI-compatible API.
 * Manually configures OpenAI client pointing at the xAI endpoint.
 */
@Configuration
@RequiredArgsConstructor
public class GrokChatConfig {

    @Bean("grokChatClient")
    @ConditionalOnProperty(prefix = "intelligence.grok", name = "api-key")
    public ChatClient grokChatClient(GrokProperties properties) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getModel())
                        .maxTokens(properties.getMaxTokens())
                        .build())
                .build();

        return ChatClient.create(chatModel);
    }
}
