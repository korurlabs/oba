package com.korur.oba.connect.intelligence.gemini.config;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.korur.oba.connect.intelligence.gemini.properties.GeminiProperties;

import lombok.RequiredArgsConstructor;

/**
 * Creates a named ChatClient for Google Gemini using OpenAI-compatible API.
 */
@Configuration
@RequiredArgsConstructor
public class GeminiChatConfig {

    @Bean("geminiChatClient")
    @ConditionalOnProperty(prefix = "intelligence.gemini", name = "api-key")
    public ChatClient geminiChatClient(GeminiProperties properties) {
        ClientHttpRequestFactorySettings timeoutSettings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofSeconds(120));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(timeoutSettings));

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .restClientBuilder(restClientBuilder)
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
