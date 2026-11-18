package com.korur.oba.connect.intelligence.core.router.impl;

// 1. Framework-level
import jakarta.annotation.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static com.korur.oba.connect.intelligence.core.constant.service.IntelligenceServiceLogConstant.PROVIDER_RESOLVED;
import static com.korur.oba.connect.intelligence.core.constant.service.IntelligenceServiceLogConstant.RESOLVING_PROVIDER;

// 3. Java SDK
import java.util.EnumMap;
import java.util.Map;

import com.korur.oba.connect.intelligence.core.constant.IntelligenceConstant;
import com.korur.oba.connect.intelligence.core.constant.IntelligenceExceptionLogConstant;
import com.korur.oba.connect.intelligence.core.enums.IntelligenceProvider;
import com.korur.oba.connect.intelligence.core.enums.IntelligenceUseCase;
import com.korur.oba.connect.intelligence.core.properties.IntelligenceRoutingProperties;
import com.korur.oba.connect.intelligence.core.router.IntelligenceRouter;
import com.korur.oba.connect.intelligence.exception.ExceptionDomainIntelligenceError;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Resolves use-cases to providers and providers to ChatClient beans.
 * Routing is driven by {@link IntelligenceRoutingProperties}.
 */
@Component
public class IntelligenceRouterImpl implements IntelligenceRouter {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(IntelligenceRouterImpl.class);

    private final IntelligenceRoutingProperties routingProperties;
    private final LoggingService loggingService;
    private final Map<IntelligenceProvider, ChatClient> chatClients;

    public IntelligenceRouterImpl(
            IntelligenceRoutingProperties routingProperties,
            LoggingService loggingService,
            @Nullable @Qualifier("anthropicChatClient") ChatClient anthropicChatClient,
            @Nullable @Qualifier("geminiChatClient") ChatClient geminiChatClient,
            @Nullable @Qualifier("deepseekChatClient") ChatClient deepseekChatClient,
            @Nullable @Qualifier("mistralChatClient") ChatClient mistralChatClient,
            @Nullable @Qualifier("grokChatClient") ChatClient grokChatClient) {
        this.routingProperties = routingProperties;
        this.loggingService = loggingService;

        this.chatClients = new EnumMap<>(IntelligenceProvider.class);
        if (anthropicChatClient != null) this.chatClients.put(IntelligenceProvider.ANTHROPIC, anthropicChatClient);
        if (geminiChatClient != null) this.chatClients.put(IntelligenceProvider.GEMINI, geminiChatClient);
        if (deepseekChatClient != null) this.chatClients.put(IntelligenceProvider.DEEPSEEK, deepseekChatClient);
        if (mistralChatClient != null) this.chatClients.put(IntelligenceProvider.MISTRAL, mistralChatClient);
        if (grokChatClient != null) this.chatClients.put(IntelligenceProvider.GROK, grokChatClient);
    }

    @Override
    public IntelligenceProvider resolveProvider(IntelligenceUseCase useCase) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, RESOLVING_PROVIDER, useCase);

        IntelligenceProvider provider = switch (useCase) {
            case CONVERSATION -> routingProperties.getConversation();
            case SCAN_ANALYSIS -> routingProperties.getScanAnalysis();
            case THREAT_ANALYSIS -> routingProperties.getThreatAnalysis();
            case TASK_GENERATION -> routingProperties.getTaskGeneration();
            case TICKET_ANALYSIS -> routingProperties.getTicketAnalysis();
        };

        if (provider == null) {
            throw new InternalServerErrorException(
                    ExceptionDomainIntelligenceError.INT_NO_PROVIDER,
                    Map.of(IntelligenceConstant.FIELD_USE_CASE, useCase.name()));
        }

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, PROVIDER_RESOLVED, provider, useCase);
        return provider;
    }

    @Override
    public ChatClient resolveChatClient(IntelligenceProvider provider) {
        ChatClient chatClient = chatClients.get(provider);

        if (chatClient == null) {
            throw new InternalServerErrorException(
                    ExceptionDomainIntelligenceError.INT_INVALID_CONFIGURATION,
                    Map.of(IntelligenceConstant.FIELD_PROVIDER, provider.name()));
        }

        return chatClient;
    }
}
