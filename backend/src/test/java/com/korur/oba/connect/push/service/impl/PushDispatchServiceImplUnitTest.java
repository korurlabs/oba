package com.korur.oba.connect.push.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.constant.service.PushDispatchServiceLogConstant;
import com.korur.oba.connect.push.client.ExpoPushClient;
import com.korur.oba.connect.push.dto.PushRequest;
import com.korur.oba.connect.push.dto.expo.ExpoPushErrorDetails;
import com.korur.oba.connect.push.dto.expo.ExpoPushMessage;
import com.korur.oba.connect.push.dto.expo.ExpoPushTicket;
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.connect.push.entity.PushDevice;
import com.korur.oba.connect.push.enums.PushKind;
import com.korur.oba.connect.push.exception.ExceptionDomainPushError;
import com.korur.oba.connect.push.helper.PushMessageHelper;
import com.korur.oba.connect.push.helper.PushTicketTracker;
import com.korur.oba.connect.push.mapper.ExpoPushMapperImpl;
import com.korur.oba.connect.push.service.PushDeviceQueryService;
import com.korur.oba.connect.push.service.PushDeviceService;
import com.korur.oba.connect.push.service.PushPreferenceQueryService;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class PushDispatchServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-06T09:00:00Z");
    private static final PushPreferenceResponse ALL_ON = new PushPreferenceResponse(true, true, true, true, true);

    @Mock
    private ExpoPushClient expoPushClient;
    @Mock
    private PushDeviceQueryService pushDeviceQueryService;
    @Mock
    private PushPreferenceQueryService pushPreferenceQueryService;
    @Mock
    private PushDeviceService pushDeviceService;
    @Mock
    private PushTicketTracker pushTicketTracker;
    @Mock
    private LoggingService loggingService;

    private PushDispatchServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PushDispatchServiceImpl(expoPushClient, pushDeviceQueryService, pushPreferenceQueryService,
                pushDeviceService, new PushMessageHelper(new ExpoPushMapperImpl()), pushTicketTracker,
                loggingService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenEnabled() {
        when(expoPushClient.isEnabled()).thenReturn(true);
    }

    private PushDevice device(UUID userId, String token) {
        return PushDevice.builder().userId(userId).expoPushToken(token).build();
    }

    private PushRequest chat(Map<UUID, PushKind> recipients) {
        return PushRequest.chat(organizationId, recipients, authorId, "#general", "Ayse Kaya", "Deploy is green",
                false, channelId, null, UUID.randomUUID(), null);
    }

    private static ExpoPushTicket ok(String id) {
        return new ExpoPushTicket("ok", id, null, null);
    }

    private static ExpoPushTicket error(String error) {
        return new ExpoPushTicket("error", null, "failed", new ExpoPushErrorDetails(error));
    }

    @SuppressWarnings("unchecked")
    private List<ExpoPushMessage> sentMessages(int calls) {
        ArgumentCaptor<List<ExpoPushMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(expoPushClient, times(calls)).send(captor.capture());
        List<ExpoPushMessage> all = new ArrayList<>();
        captor.getAllValues().forEach(all::addAll);
        return all;
    }

    @Nested
    class Skipped {

        @Test
        void sendingOff_callsNothingAndReadsNothing() {
            // Arrange
            when(expoPushClient.isEnabled()).thenReturn(false);

            // Act
            service.dispatch(chat(Map.of(recipientId, PushKind.MENTION)));

            // Assert
            verify(expoPushClient, never()).send(anyList());
            verifyNoInteractions(pushDeviceQueryService, pushPreferenceQueryService, pushDeviceService,
                    pushTicketTracker);
            verify(loggingService).debug(any(), eq(LogLayer.SERVICE),
                    eq(PushDispatchServiceLogConstant.DISPATCH_SKIPPED_DISABLED), any(Object[].class));
        }

        @Test
        void nullOrNoRecipients_returnsAtOnce() {
            // Act
            service.dispatch(null);
            service.dispatch(chat(Map.of()));
            service.dispatch(chat(null));

            // Assert
            verifyNoInteractions(expoPushClient, pushDeviceQueryService, pushPreferenceQueryService);
        }

        @Test
        void authorAsTheOnlyRecipient_sendsNothing() {
            // Arrange
            givenEnabled();

            // Act
            service.dispatch(chat(Map.of(authorId, PushKind.MENTION)));

            // Assert
            verify(expoPushClient, never()).send(anyList());
            verify(pushDeviceQueryService, never()).getDevicesByUserIds(anyCollection());
        }
    }

    @Nested
    class Recipients {

        @Test
        void authorAmongRecipients_neverReceives() {
            // Arrange
            givenEnabled();
            Map<UUID, PushKind> recipients = new LinkedHashMap<>();
            recipients.put(authorId, PushKind.MENTION);
            recipients.put(recipientId, PushKind.MENTION);
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(recipientId)))
                    .thenReturn(Map.of(recipientId, ALL_ON));
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId)))
                    .thenReturn(List.of(device(recipientId, "ExponentPushToken[r1]")));
            when(expoPushClient.send(anyList())).thenReturn(List.of(ok("t1")));

            // Act
            service.dispatch(chat(recipients));

            // Assert
            List<ExpoPushMessage> sent = sentMessages(1);
            assertThat(sent).extracting(ExpoPushMessage::to).containsExactly("ExponentPushToken[r1]");
        }

        @Test
        void switchOffForTheKind_dropsThatRecipientOnly() {
            // Arrange
            givenEnabled();
            UUID silenced = UUID.randomUUID();
            Map<UUID, PushKind> recipients = new LinkedHashMap<>();
            recipients.put(silenced, PushKind.EVERYONE);
            recipients.put(recipientId, PushKind.EVERYONE);
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(silenced, recipientId)))
                    .thenReturn(Map.of(
                            silenced, new PushPreferenceResponse(true, false, true, true, true),
                            recipientId, ALL_ON));
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId)))
                    .thenReturn(List.of(device(recipientId, "ExponentPushToken[r1]")));
            when(expoPushClient.send(anyList())).thenReturn(List.of(ok("t1")));

            // Act
            service.dispatch(chat(recipients));

            // Assert
            assertThat(sentMessages(1)).extracting(ExpoPushMessage::to).containsExactly("ExponentPushToken[r1]");
        }

        @Test
        void everyRecipientSilenced_readsNoDevicesAndSendsNothing() {
            // Arrange
            givenEnabled();
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(recipientId)))
                    .thenReturn(Map.of(recipientId, new PushPreferenceResponse(false, true, true, true, true)));

            // Act
            service.dispatch(chat(Map.of(recipientId, PushKind.MENTION)));

            // Assert
            verify(pushDeviceQueryService, never()).getDevicesByUserIds(anyCollection());
            verify(expoPushClient, never()).send(anyList());
        }

        @Test
        void previewsOff_sendsTheHiddenLineWithoutTheText() {
            // Arrange
            givenEnabled();
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(recipientId)))
                    .thenReturn(Map.of(recipientId, new PushPreferenceResponse(true, true, true, true, false)));
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId)))
                    .thenReturn(List.of(device(recipientId, "ExponentPushToken[r1]")));
            when(expoPushClient.send(anyList())).thenReturn(List.of(ok("t1")));

            // Act
            service.dispatch(chat(Map.of(recipientId, PushKind.MENTION)));

            // Assert
            ExpoPushMessage message = sentMessages(1).getFirst();
            assertThat(message.body()).isEqualTo("New mention in #general");
            assertThat(message.body()).doesNotContain("Deploy is green").doesNotContain("Ayse");
        }

        @Test
        void recipientWithTwoDevices_getsOneMessageEach() {
            // Arrange
            givenEnabled();
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(recipientId)))
                    .thenReturn(Map.of(recipientId, ALL_ON));
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId)))
                    .thenReturn(List.of(device(recipientId, "ExponentPushToken[a]"),
                            device(recipientId, "ExponentPushToken[b]")));
            when(expoPushClient.send(anyList())).thenReturn(List.of(ok("t1"), ok("t2")));

            // Act
            service.dispatch(chat(Map.of(recipientId, PushKind.REPLY)));

            // Assert
            assertThat(sentMessages(1)).extracting(ExpoPushMessage::to)
                    .containsExactly("ExponentPushToken[a]", "ExponentPushToken[b]");
        }

        @Test
        void readWithdrawal_reachesTheReadersOwnDevicesAndIgnoresSwitches() {
            // Arrange
            givenEnabled();
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId)))
                    .thenReturn(List.of(device(recipientId, "ExponentPushToken[r1]")));
            when(expoPushClient.send(anyList())).thenReturn(List.of(ok("t1")));

            // Act
            service.dispatch(PushRequest.read(organizationId, recipientId, channelId, null));

            // Assert
            verify(pushPreferenceQueryService, never()).getPreferencesByUserIds(anyCollection());
            ExpoPushMessage message = sentMessages(1).getFirst();
            assertThat(message.title()).isNull();
            assertThat(message.body()).isNull();
            assertThat(message.data().type()).isEqualTo(PushKind.READ);
        }
    }

    @Nested
    class Sending {

        private List<PushDevice> manyDevices(int count) {
            List<PushDevice> devices = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                devices.add(device(recipientId, "ExponentPushToken[" + index + "]"));
            }
            return devices;
        }

        @Test
        void twoHundredFiftyDevices_goOutInChunksOfOneHundred() {
            // Arrange
            givenEnabled();
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(recipientId)))
                    .thenReturn(Map.of(recipientId, ALL_ON));
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId))).thenReturn(manyDevices(250));
            when(expoPushClient.send(anyList())).thenAnswer(invocation -> {
                List<?> chunk = invocation.getArgument(0);
                List<ExpoPushTicket> tickets = new ArrayList<>();
                chunk.forEach(message -> tickets.add(ok(UUID.randomUUID().toString())));
                return tickets;
            });

            // Act
            service.dispatch(chat(Map.of(recipientId, PushKind.MENTION)));

            // Assert
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ExpoPushMessage>> captor = ArgumentCaptor.forClass(List.class);
            verify(expoPushClient, times(3)).send(captor.capture());
            assertThat(captor.getAllValues()).extracting(List::size).containsExactly(100, 100, 50);
        }

        @Test
        void acceptedTickets_areTrackedWithTheirTokenAndSendTime() {
            // Arrange
            givenEnabled();
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(recipientId)))
                    .thenReturn(Map.of(recipientId, ALL_ON));
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId)))
                    .thenReturn(List.of(device(recipientId, "ExponentPushToken[a]"),
                            device(recipientId, "ExponentPushToken[b]")));
            when(expoPushClient.send(anyList())).thenReturn(List.of(ok("t1"), error("MessageRateExceeded")));

            // Act
            service.dispatch(chat(Map.of(recipientId, PushKind.MENTION)));

            // Assert
            verify(pushTicketTracker).track(Map.of("t1", "ExponentPushToken[a]"), NOW);
            verify(pushDeviceService, never()).removeTokens(anyCollection());
            verify(loggingService).warn(any(), eq(LogLayer.SERVICE),
                    eq(PushDispatchServiceLogConstant.TICKET_ERROR), any(Object[].class));
        }

        @Test
        void deviceNotRegisteredTicket_removesThatToken() {
            // Arrange
            givenEnabled();
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(recipientId)))
                    .thenReturn(Map.of(recipientId, ALL_ON));
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId)))
                    .thenReturn(List.of(device(recipientId, "ExponentPushToken[dead]"),
                            device(recipientId, "ExponentPushToken[live]")));
            when(expoPushClient.send(anyList())).thenReturn(List.of(error("DeviceNotRegistered"), ok("t2")));

            // Act
            service.dispatch(chat(Map.of(recipientId, PushKind.MENTION)));

            // Assert
            verify(pushDeviceService).removeTokens(List.of("ExponentPushToken[dead]"));
            verify(pushTicketTracker).track(Map.of("t2", "ExponentPushToken[live]"), NOW);
        }

        @Test
        void expoFailureOnOneChunk_isLoggedAndTheOtherChunksStillGoOut() {
            // Arrange
            givenEnabled();
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(recipientId)))
                    .thenReturn(Map.of(recipientId, ALL_ON));
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId))).thenReturn(manyDevices(150));
            BadGatewayException unavailable = new BadGatewayException(ExceptionDomainPushError.UPS_EXPO_UNAVAILABLE);
            List<ExpoPushTicket> second = new ArrayList<>();
            for (int index = 0; index < 50; index++) {
                second.add(ok("t" + index));
            }
            when(expoPushClient.send(anyList())).thenThrow(unavailable).thenReturn(second);

            // Act and assert
            assertThatCode(() -> service.dispatch(chat(Map.of(recipientId, PushKind.MENTION))))
                    .doesNotThrowAnyException();
            verify(expoPushClient, times(2)).send(anyList());
            verify(loggingService).error(any(), eq(LogLayer.SERVICE), eq(PushDispatchServiceLogConstant.CHUNK_FAILED),
                    eq(unavailable), any(Object[].class));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> tracked = ArgumentCaptor.forClass(Map.class);
            verify(pushTicketTracker).track(tracked.capture(), eq(NOW));
            assertThat(tracked.getValue()).hasSize(50);
        }

        @Test
        void ticketCountDiffersFromMessageCount_warnsAndHandlesTheMatchedPrefix() {
            // Arrange
            givenEnabled();
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(recipientId)))
                    .thenReturn(Map.of(recipientId, ALL_ON));
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId)))
                    .thenReturn(List.of(device(recipientId, "ExponentPushToken[a]"),
                            device(recipientId, "ExponentPushToken[b]")));
            when(expoPushClient.send(anyList())).thenReturn(List.of(ok("t1")));

            // Act
            service.dispatch(chat(Map.of(recipientId, PushKind.MENTION)));

            // Assert
            verify(loggingService).warn(any(), eq(LogLayer.SERVICE),
                    eq(PushDispatchServiceLogConstant.TICKET_COUNT_MISMATCH), any(Object[].class));
            verify(pushTicketTracker).track(Map.of("t1", "ExponentPushToken[a]"), NOW);
        }

        @Test
        void tokenRemovalFailure_isLoggedAndDoesNotFailTheDispatch() {
            // Arrange
            givenEnabled();
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(recipientId)))
                    .thenReturn(Map.of(recipientId, ALL_ON));
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId)))
                    .thenReturn(List.of(device(recipientId, "ExponentPushToken[dead]")));
            when(expoPushClient.send(anyList())).thenReturn(List.of(error("DeviceNotRegistered")));
            RuntimeException failure = new IllegalStateException("database down");
            doThrow(failure).when(pushDeviceService).removeTokens(anyCollection());

            // Act and assert
            assertThatCode(() -> service.dispatch(chat(Map.of(recipientId, PushKind.MENTION))))
                    .doesNotThrowAnyException();
            verify(loggingService).error(any(), eq(LogLayer.SERVICE),
                    eq(PushDispatchServiceLogConstant.TOKEN_REMOVAL_FAILED), eq(failure), any(Object[].class));
        }

        @Test
        void trackerIsCalledWithNoTicketsWhenNothingWasAccepted() {
            // Arrange
            givenEnabled();
            when(pushPreferenceQueryService.getPreferencesByUserIds(Set.of(recipientId)))
                    .thenReturn(Map.of(recipientId, ALL_ON));
            when(pushDeviceQueryService.getDevicesByUserIds(Set.of(recipientId))).thenReturn(List.of());

            // Act
            service.dispatch(chat(Map.of(recipientId, PushKind.MENTION)));

            // Assert
            verify(expoPushClient, never()).send(anyList());
            verify(pushTicketTracker).track(anyMap(), eq(NOW));
        }
    }
}
