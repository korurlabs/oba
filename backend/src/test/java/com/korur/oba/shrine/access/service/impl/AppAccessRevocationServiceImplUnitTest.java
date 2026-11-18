package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party

// 3. Java SDK
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.event.AppAccessRevokedEvent;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.service.AppAccessQueryService;

@ExtendWith(MockitoExtension.class)
class AppAccessRevocationServiceImplUnitTest {

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @Mock
    private AppGrantRepository appGrantRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private AppAccessRevocationServiceImpl appAccessRevocationServiceImpl;

    @Nested
    class PublishLostAppAccess {

        @Test
        void noHolders_doesNothing() {
            // Act
            appAccessRevocationServiceImpl.publishLostAppAccess(UUID.randomUUID(), Map.of());

            // Assert
            verifyNoInteractions(appGrantRepository, appAccessQueryService, applicationEventPublisher);
        }

        @Test
        void holders_flushesThenPublishesOneEventPerUserAndLostApp() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID lostBoth = UUID.randomUUID();
            UUID keptKazan = UUID.randomUUID();
            when(appAccessQueryService.hasAppAccess(organizationId, lostBoth, App.KAZAN)).thenReturn(false);
            when(appAccessQueryService.hasAppAccess(organizationId, keptKazan, App.KAZAN)).thenReturn(true);
            when(appAccessQueryService.hasAppAccess(organizationId, lostBoth, App.KAYA)).thenReturn(false);

            // Act
            appAccessRevocationServiceImpl.publishLostAppAccess(organizationId, Map.of(
                    App.KAZAN, Set.of(lostBoth, keptKazan), App.KAYA, Set.of(lostBoth)));

            // Assert
            InOrder order = inOrder(appGrantRepository, appAccessQueryService);
            order.verify(appGrantRepository).flush();
            order.verify(appAccessQueryService, atLeastOnce()).hasAppAccess(any(), any(), any());
            verify(applicationEventPublisher)
                    .publishEvent(new AppAccessRevokedEvent(lostBoth, organizationId, App.KAZAN));
            verify(applicationEventPublisher)
                    .publishEvent(new AppAccessRevokedEvent(lostBoth, organizationId, App.KAYA));
            verify(applicationEventPublisher, never())
                    .publishEvent(new AppAccessRevokedEvent(keptKazan, organizationId, App.KAZAN));
        }
    }
}
