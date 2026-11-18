package com.korur.oba.connect.intelligence.core.service.impl;

// 1. Framework-level
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.content.Media;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

// 2. Third-party
import lombok.RequiredArgsConstructor;

import static com.korur.oba.connect.intelligence.core.constant.service.IntelligenceServiceLogConstant.*;

// 3. Java SDK
import java.util.Map;

import com.korur.oba.connect.intelligence.core.constant.IntelligenceConstant;
import com.korur.oba.connect.retry.annotation.ConnectRetry;
import com.korur.oba.connect.retry.enums.ConnectRetryPolicy;
import com.korur.oba.connect.intelligence.core.constant.IntelligenceExceptionLogConstant;
import com.korur.oba.connect.intelligence.core.enums.IntelligenceProvider;
import com.korur.oba.connect.intelligence.core.enums.IntelligenceUseCase;
import com.korur.oba.connect.intelligence.core.router.IntelligenceRouter;
import com.korur.oba.connect.intelligence.core.service.IntelligenceService;
import com.korur.oba.connect.intelligence.exception.ExceptionDomainIntelligenceError;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implements multi-model AI operations by delegating to the intelligence router.
 */
@Service
@RequiredArgsConstructor
public class IntelligenceServiceImpl implements IntelligenceService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(IntelligenceServiceImpl.class);

    private final IntelligenceRouter intelligenceRouter;
    private final LoggingService loggingService;

    @Override
    public boolean isAvailable(IntelligenceProvider provider) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, CHECKING_AVAILABILITY, provider);

        try {
            ChatClient chatClient = intelligenceRouter.resolveChatClient(provider);
            boolean available = chatClient != null;

            if (available) {
                loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, PROVIDER_AVAILABLE, provider);
            } else {
                loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, PROVIDER_NOT_AVAILABLE, provider);
            }

            return available;
        } catch (Exception e) {
            loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, PROVIDER_NOT_AVAILABLE, provider);
            return false;
        }
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.INTELLIGENCE)
    public String chat(IntelligenceUseCase useCase, String systemPrompt, String userMessage) {
        IntelligenceProvider provider = intelligenceRouter.resolveProvider(useCase);
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, SENDING_CHAT_REQUEST, provider, useCase);

        return executeChat(provider, systemPrompt, userMessage);
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.INTELLIGENCE)
    public String chat(IntelligenceProvider provider, String systemPrompt, String userMessage) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, SENDING_CHAT_REQUEST, provider, provider);

        return executeChat(provider, systemPrompt, userMessage);
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.INTELLIGENCE)
    public String chatWithImage(IntelligenceUseCase useCase, String systemPrompt, String userMessage,
                                byte[] imageBytes, String mimeType) {
        IntelligenceProvider provider = intelligenceRouter.resolveProvider(useCase);
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, SENDING_CHAT_REQUEST, provider, useCase);

        try {
            ChatClient chatClient = intelligenceRouter.resolveChatClient(provider);
            UserMessage multimodalMessage = buildMultimodalMessage(userMessage, imageBytes, mimeType);

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(multimodalMessage)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                        IntelligenceExceptionLogConstant.PROVIDER_EMPTY_RESPONSE, provider);
                throw new InternalServerErrorException(
                        ExceptionDomainIntelligenceError.UPS_EMPTY_RESPONSE,
                        Map.of(IntelligenceConstant.FIELD_PROVIDER, provider.name()));
            }

            loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, CHAT_REQUEST_COMPLETED, provider);
            return response;
        } catch (InternalServerErrorException e) {
            throw e;
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    IntelligenceExceptionLogConstant.PROVIDER_REQUEST_FAILED, provider, e);
            throw new InternalServerErrorException(
                    ExceptionDomainIntelligenceError.UPS_REQUEST_FAILED,
                    Map.of(
                            IntelligenceConstant.FIELD_PROVIDER, provider.name(),
                            IntelligenceConstant.FIELD_REASON, e.getMessage()));
        }
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.INTELLIGENCE)
    public <T> T chatWithImageEntity(IntelligenceUseCase useCase, String model, String systemPrompt, String userMessage,
                                     byte[] bytes, String mimeType, ParameterizedTypeReference<T> type) {
        IntelligenceProvider provider = intelligenceRouter.resolveProvider(useCase);
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, SENDING_CHAT_REQUEST, provider, useCase);

        try {
            ChatClient chatClient = intelligenceRouter.resolveChatClient(provider);
            UserMessage multimodalMessage = buildMultimodalMessage(userMessage, bytes, mimeType);

            ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(multimodalMessage);

            // Apply runtime model override when provided. Falls back to the provider's default model
            // configured via spring.ai.<provider>.chat.options.model when null/blank.
            if (model != null && !model.isBlank()) {
                ChatOptions options = ChatOptions.builder().model(model).build();
                request = request.options(options);
            }

            T entity = request.call().entity(type);

            if (entity == null) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                        IntelligenceExceptionLogConstant.PROVIDER_EMPTY_RESPONSE, provider);
                throw new BadGatewayException(
                        ExceptionDomainIntelligenceError.UPS_EMPTY_RESPONSE,
                        Map.of(IntelligenceConstant.FIELD_PROVIDER, provider.name()));
            }

            loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, CHAT_REQUEST_COMPLETED, provider);
            return entity;
        } catch (BadGatewayException e) {
            throw e;
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    IntelligenceExceptionLogConstant.PROVIDER_REQUEST_FAILED, provider, e);
            throw new BadGatewayException(
                    ExceptionDomainIntelligenceError.UPS_REQUEST_FAILED,
                    Map.of(
                            IntelligenceConstant.FIELD_PROVIDER, provider.name(),
                            IntelligenceConstant.FIELD_REASON, e.getMessage()));
        }
    }

    private UserMessage buildMultimodalMessage(String userMessage, byte[] bytes, String mimeType) {
        Media media = Media.builder()
                .mimeType(MimeType.valueOf(mimeType))
                .data(bytes)
                .build();

        return UserMessage.builder()
                .text(userMessage)
                .media(media)
                .build();
    }

    private String executeChat(IntelligenceProvider provider, String systemPrompt, String userMessage) {
        try {
            ChatClient chatClient = intelligenceRouter.resolveChatClient(provider);

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                        IntelligenceExceptionLogConstant.PROVIDER_EMPTY_RESPONSE, provider);
                throw new InternalServerErrorException(
                        ExceptionDomainIntelligenceError.UPS_EMPTY_RESPONSE,
                        Map.of(IntelligenceConstant.FIELD_PROVIDER, provider.name()));
            }

            loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, CHAT_REQUEST_COMPLETED, provider);
            return response;
        } catch (InternalServerErrorException e) {
            throw e;
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    IntelligenceExceptionLogConstant.PROVIDER_REQUEST_FAILED, provider, e);
            throw new InternalServerErrorException(
                    ExceptionDomainIntelligenceError.UPS_REQUEST_FAILED,
                    Map.of(
                            IntelligenceConstant.FIELD_PROVIDER, provider.name(),
                            IntelligenceConstant.FIELD_REASON, e.getMessage()));
        }
    }
}
