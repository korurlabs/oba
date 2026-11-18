package com.korur.oba.apps.kazan.dailyupdate.listener;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.constant.DailyUpdateExceptionLogConstant;
import com.korur.oba.apps.kazan.dailyupdate.constant.listener.DailyUpdateListenerLogConstant;
import com.korur.oba.apps.kazan.dailyupdate.event.DailyUpdateNudgedEvent;
import com.korur.oba.connect.push.dto.PushRequest;
import com.korur.oba.connect.push.enums.PushKind;
import com.korur.oba.connect.push.service.PushDispatchService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;

@ExtendWith(MockitoExtension.class)
class DailyUpdateNudgePushListenerUnitTest {

    @Mock
    private AppAccessQueryService appAccessQueryService;
    @Mock
    private PushDispatchService pushDispatchService;
    @Mock
    private LoggingService loggingService;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final DailyUpdateNudgedEvent event = new DailyUpdateNudgedEvent(organizationId, UUID.randomUUID(),
            recipientId, senderId, "Ayşe Kaya", LocalDate.of(2026, 11, 6));

    private DailyUpdateNudgePushListener listener;

    @BeforeEach
    void setUp() {
        listener = new DailyUpdateNudgePushListener(appAccessQueryService, pushDispatchService, loggingService);
    }

    @Test
    void onNudged_dispatchesANudgePushToTheRecipientWithTheDeadline() {
        // Arrange
        when(appAccessQueryService.hasAppAccess(organizationId, recipientId, App.KAZAN)).thenReturn(true);

        // Act
        listener.onNudged(event);

        // Assert
        ArgumentCaptor<PushRequest> request = ArgumentCaptor.forClass(PushRequest.class);
        verify(pushDispatchService).dispatch(request.capture());
        assertThat(request.getValue()).isEqualTo(PushRequest.nudge(organizationId, recipientId, senderId,
                "Ayşe Kaya", "10:30"));
        assertThat(request.getValue().recipients()).containsEntry(recipientId, PushKind.NUDGE);
    }

    @Test
    void onNudged_recipientWithoutKazan_dispatchesNothingAndLogsIt() {
        // Arrange
        when(appAccessQueryService.hasAppAccess(organizationId, recipientId, App.KAZAN)).thenReturn(false);

        // Act
        listener.onNudged(event);

        // Assert
        verify(pushDispatchService, never()).dispatch(any(PushRequest.class));
        verify(loggingService).info(any(), eq(LogLayer.HANDLER),
                eq(DailyUpdateListenerLogConstant.DAILY_UPDATE_NUDGE_PUSH_SKIPPED_WITHOUT_KAZAN),
                any(Object[].class));
    }

    @Test
    void onNudged_accessReadFails_dispatchesNothingAndLogsTheFailure() {
        // Arrange
        RuntimeException failure = new IllegalStateException("database down");
        when(appAccessQueryService.hasAppAccess(organizationId, recipientId, App.KAZAN)).thenThrow(failure);

        // Act and assert
        assertThatCode(() -> listener.onNudged(event)).doesNotThrowAnyException();
        verify(pushDispatchService, never()).dispatch(any(PushRequest.class));
        verify(loggingService).error(any(), eq(LogLayer.HANDLER),
                eq(DailyUpdateExceptionLogConstant.DAILY_UPDATE_NUDGE_PUSH_FAILED), eq(failure), any(Object[].class));
    }

    @Test
    void onNudged_dispatchFails_isLoggedAndNotRethrown() {
        // Arrange
        when(appAccessQueryService.hasAppAccess(organizationId, recipientId, App.KAZAN)).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(pushDispatchService).dispatch(any(PushRequest.class));

        // Act and assert
        assertThatCode(() -> listener.onNudged(event)).doesNotThrowAnyException();
        verify(loggingService).error(any(), any(), any(String.class), any(Throwable.class), any(Object[].class));
    }
}
