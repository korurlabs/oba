package com.korur.oba.connect.routine.core.router.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.routine.core.client.RoutineClient;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;
import com.korur.oba.connect.routine.core.router.RoutineRouter;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.service.LoggingService;

class RoutineRouterImplUnitTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(LoggingService.class, () -> mock(LoggingService.class))
            .withBean(RoutineRouterImpl.class);

    private static RoutineClient clientFor(RoutineProvider provider) {
        RoutineClient client = mock(RoutineClient.class);
        when(client.provider()).thenReturn(provider);
        return client;
    }

    @Configuration
    static class OneAnthropicClient {
        @Bean
        RoutineClient anthropicClient() {
            return clientFor(RoutineProvider.ANTHROPIC);
        }
    }

    @Configuration
    static class TwoAnthropicClients {
        @Bean
        RoutineClient firstAnthropicClient() {
            return clientFor(RoutineProvider.ANTHROPIC);
        }

        @Bean
        RoutineClient secondAnthropicClient() {
            return clientFor(RoutineProvider.ANTHROPIC);
        }
    }

    @Test
    void start_oneClientPerProvider_resolvesEachProvider() {
        contextRunner.withUserConfiguration(OneAnthropicClient.class).run(context -> {
            // Assert
            assertThat(context).hasNotFailed();
            RoutineRouter router = context.getBean(RoutineRouter.class);
            RoutineClient client = context.getBean(RoutineClient.class);
            for (RoutineProvider provider : RoutineProvider.values()) {
                assertThat(router.resolveClient(provider)).isSameAs(client);
            }
        });
    }

    @Test
    void start_providerWithoutClient_failsWithClientMissing() {
        contextRunner.run(context -> {
            // Assert
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(InternalServerErrorException.class)
                    .rootCause()
                    .hasFieldOrPropertyWithValue("errorCode", "RTN_INT_001");
        });
    }

    @Test
    void start_twoClientsForOneProvider_failsWithClientDuplicate() {
        contextRunner.withUserConfiguration(TwoAnthropicClients.class).run(context -> {
            // Assert
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(InternalServerErrorException.class)
                    .rootCause()
                    .hasFieldOrPropertyWithValue("errorCode", "RTN_INT_002");
        });
    }
}
