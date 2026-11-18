package com.korur.oba.apps.kazan.ticket.listener;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.ticket.constant.listener.IncidentPushListenerLogConstant;
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;
import com.korur.oba.apps.kazan.ticket.event.IncidentOpenedEvent;
import com.korur.oba.connect.push.dto.PushRequest;
import com.korur.oba.connect.push.enums.PushKind;
import com.korur.oba.connect.push.service.PushDispatchService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;

@ExtendWith(MockitoExtension.class)
class IncidentPushListenerUnitTest {

    @Mock
    private BoardMemberQueryService boardMemberQueryService;
    @Mock
    private AppAccessQueryService appAccessQueryService;
    @Mock
    private PushDispatchService pushDispatchService;
    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private IncidentPushListener listener;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID boardId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    private IncidentOpenedEvent event(UUID actor) {
        return new IncidentOpenedEvent(organizationId, boardId, "Kazan Platform", UUID.randomUUID(), "KZN-311",
                "Payment webhooks return 502", TicketPriority.P1, actor);
    }

    private void givenKazan(UUID userId, boolean granted) {
        when(appAccessQueryService.hasAppAccess(organizationId, userId, App.KAZAN)).thenReturn(granted);
    }

    @Test
    void onIncidentOpened_boardMembers_dispatchesAnIncidentPushToEachWithTheActorExcluded() {
        // Arrange
        when(boardMemberQueryService.getMemberUserIds(List.of(boardId))).thenReturn(List.of(actorId, memberId));
        givenKazan(actorId, true);
        givenKazan(memberId, true);

        // Act
        listener.onIncidentOpened(event(actorId));

        // Assert
        ArgumentCaptor<PushRequest> captor = ArgumentCaptor.forClass(PushRequest.class);
        verify(pushDispatchService).dispatch(captor.capture());
        PushRequest request = captor.getValue();
        assertThat(request.recipients()).containsOnlyKeys(actorId, memberId);
        assertThat(request.recipients().values()).containsOnly(PushKind.INCIDENT);
        assertThat(request.actorId()).isEqualTo(actorId);
        assertThat(request.conversation()).isEqualTo("Kazan Platform");
        assertThat(request.priority()).isEqualTo("P1");
        assertThat(request.ticketKey()).isEqualTo("KZN-311");
        assertThat(request.text()).isEqualTo("Payment webhooks return 502");
        assertThat(request.organizationId()).isEqualTo(organizationId);
    }

    @Test
    void onIncidentOpened_monitoringSource_hasNoActor() {
        // Arrange
        when(boardMemberQueryService.getMemberUserIds(List.of(boardId))).thenReturn(List.of(memberId));
        givenKazan(memberId, true);

        // Act
        listener.onIncidentOpened(event(null));

        // Assert
        ArgumentCaptor<PushRequest> captor = ArgumentCaptor.forClass(PushRequest.class);
        verify(pushDispatchService).dispatch(captor.capture());
        assertThat(captor.getValue().actorId()).isNull();
    }

    @Test
    void onIncidentOpened_boardWithoutMembers_dispatchesNothingAndLogsIt() {
        // Arrange
        when(boardMemberQueryService.getMemberUserIds(List.of(boardId))).thenReturn(List.of());

        // Act
        listener.onIncidentOpened(event(actorId));

        // Assert
        verify(pushDispatchService, never()).dispatch(any(PushRequest.class));
        verify(loggingService).info(any(), eq(LogLayer.HANDLER),
                eq(IncidentPushListenerLogConstant.INCIDENT_PUSH_NO_RECIPIENTS), any(Object[].class));
    }

    @Test
    void onIncidentOpened_memberWithoutKazan_isLeftOutOfThePush() {
        // Arrange
        UUID revokedId = UUID.randomUUID();
        when(boardMemberQueryService.getMemberUserIds(List.of(boardId))).thenReturn(List.of(memberId, revokedId));
        givenKazan(memberId, true);
        givenKazan(revokedId, false);

        // Act
        listener.onIncidentOpened(event(actorId));

        // Assert
        ArgumentCaptor<PushRequest> captor = ArgumentCaptor.forClass(PushRequest.class);
        verify(pushDispatchService).dispatch(captor.capture());
        assertThat(captor.getValue().recipients()).containsOnlyKeys(memberId);
    }

    @Test
    void onIncidentOpened_noMemberWithKazan_dispatchesNothingAndLogsIt() {
        // Arrange
        when(boardMemberQueryService.getMemberUserIds(List.of(boardId))).thenReturn(List.of(memberId));
        givenKazan(memberId, false);

        // Act
        listener.onIncidentOpened(event(actorId));

        // Assert
        verify(pushDispatchService, never()).dispatch(any(PushRequest.class));
        verify(loggingService).info(any(), eq(LogLayer.HANDLER),
                eq(IncidentPushListenerLogConstant.INCIDENT_PUSH_NO_RECIPIENTS_WITH_KAZAN), any(Object[].class));
    }

    @Test
    void onIncidentOpened_accessReadFails_dispatchesNothingAndLogsTheFailure() {
        // Arrange
        RuntimeException failure = new IllegalStateException("database down");
        when(boardMemberQueryService.getMemberUserIds(List.of(boardId))).thenReturn(List.of(memberId));
        when(appAccessQueryService.hasAppAccess(organizationId, memberId, App.KAZAN)).thenThrow(failure);

        // Act and assert
        assertThatCode(() -> listener.onIncidentOpened(event(actorId))).doesNotThrowAnyException();
        verify(pushDispatchService, never()).dispatch(any(PushRequest.class));
        verify(loggingService).error(any(), eq(LogLayer.HANDLER),
                eq(IncidentPushListenerLogConstant.INCIDENT_PUSH_FAILED), eq(failure), any(Object[].class));
    }

    @Test
    void onIncidentOpened_dispatchFails_isLoggedAndNotRethrown() {
        // Arrange
        RuntimeException failure = new IllegalStateException("expo down");
        when(boardMemberQueryService.getMemberUserIds(List.of(boardId))).thenReturn(List.of(memberId));
        givenKazan(memberId, true);
        doThrow(failure).when(pushDispatchService).dispatch(any(PushRequest.class));

        // Act and assert
        assertThatCode(() -> listener.onIncidentOpened(event(actorId))).doesNotThrowAnyException();
        verify(loggingService).error(any(), eq(LogLayer.HANDLER),
                eq(IncidentPushListenerLogConstant.INCIDENT_PUSH_FAILED), eq(failure), any(Object[].class));
    }

    @Test
    void onIncidentOpened_memberLookupFails_isLoggedAndNotRethrown() {
        // Arrange
        RuntimeException failure = new IllegalStateException("database down");
        when(boardMemberQueryService.getMemberUserIds(List.of(boardId))).thenThrow(failure);

        // Act and assert
        assertThatCode(() -> listener.onIncidentOpened(event(actorId))).doesNotThrowAnyException();
        verify(pushDispatchService, never()).dispatch(any(PushRequest.class));
        verify(loggingService).error(any(), eq(LogLayer.HANDLER),
                eq(IncidentPushListenerLogConstant.INCIDENT_PUSH_FAILED), eq(failure), any(Object[].class));
    }
}
