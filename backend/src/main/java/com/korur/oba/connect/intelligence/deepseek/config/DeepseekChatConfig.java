package com.korur.oba.connect.intelligence.deepseek.config;

// 1. Framework-level
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates a named ChatClient for Deepseek.
 * Relies on Spring AI auto-configuration for the underlying ChatModel.
 *
 * Gated by ConditionalOnClass on DeepSeekChatModel so the configuration is
 * silently skipped when the Deepseek starter is not on the classpath.
 * ConditionalOnBean is intentionally NOT used here: it is documented to be
 * unreliable on user @Configuration classes because the conditional is
 * evaluated before the model auto-configuration has registered the bean.
 */
@Configuration
@ConditionalOnClass(DeepSeekChatModel.class)
public class DeepseekChatConfig {

    @Bean("deepseekChatClient")
    public ChatClient deepseekChatClient(DeepSeekChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
