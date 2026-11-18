package com.korur.oba.connect.intelligence.core.router;

// 1. Framework-level
import org.springframework.ai.chat.client.ChatClient;

import com.korur.oba.connect.intelligence.core.enums.IntelligenceProvider;
import com.korur.oba.connect.intelligence.core.enums.IntelligenceUseCase;

/**
 * Resolves use-cases to providers and providers to ChatClient instances.
 */
public interface IntelligenceRouter {

    /**
     * Resolves which provider handles a given use-case.
     */
    IntelligenceProvider resolveProvider(IntelligenceUseCase useCase);

    /**
     * Resolves the ChatClient for a given provider.
     */
    ChatClient resolveChatClient(IntelligenceProvider provider);
}
