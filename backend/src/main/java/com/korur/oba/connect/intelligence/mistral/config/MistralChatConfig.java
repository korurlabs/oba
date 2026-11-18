package com.korur.oba.connect.intelligence.mistral.config;

// 1. Framework-level
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates a named ChatClient for Mistral AI.
 * Relies on Spring AI auto-configuration for the underlying ChatModel.
 *
 * Gated by ConditionalOnClass on MistralAiChatModel so the configuration is
 * silently skipped when the Mistral starter is not on the classpath.
 * ConditionalOnBean is intentionally NOT used here: it is documented to be
 * unreliable on user @Configuration classes because the conditional is
 * evaluated before the model auto-configuration has registered the bean.
 */
@Configuration
@ConditionalOnClass(MistralAiChatModel.class)
public class MistralChatConfig {

    @Bean("mistralChatClient")
    public ChatClient mistralChatClient(MistralAiChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
