package com.korur.oba.connect.intelligence.anthropic.config;

// 1. Framework-level
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates a named ChatClient for Anthropic Claude.
 * Relies on Spring AI auto-configuration for the underlying ChatModel.
 *
 * Gated by ConditionalOnClass on AnthropicChatModel so the configuration is
 * silently skipped when the Anthropic starter is not on the classpath.
 * ConditionalOnBean is intentionally NOT used here: it is documented to be
 * unreliable on user @Configuration classes because the conditional is
 * evaluated before the model auto-configuration has registered the bean,
 * which caused anthropicChatClient to never be created at startup.
 */
@Configuration
@ConditionalOnClass(AnthropicChatModel.class)
public class AnthropicChatConfig {

    @Bean("anthropicChatClient")
    public ChatClient anthropicChatClient(AnthropicChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
