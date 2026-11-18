package com.korur.oba.connect.push.scheduler;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.push.client.ExpoPushClient;
import com.korur.oba.connect.push.constant.scheduler.PushReceiptSchedulerLogConstant;
import com.korur.oba.connect.push.dto.expo.ExpoPushErrorDetails;
import com.korur.oba.connect.push.dto.expo.ExpoPushReceipt;
import com.korur.oba.connect.push.exception.ExceptionDomainPushError;
import com.korur.oba.connect.push.helper.PushTicketTracker;
import com.korur.oba.connect.push.service.PushDeviceService;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class PushReceiptSchedulerUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-06T09:00:00Z");

    @Mock
    private ExpoPushClient expoPushClient;
    @Mock
    private PushTicketTracker pushTicketTracker;
    @Mock
    private PushDeviceService pushDeviceService;
    @Mock
    private LoggingService loggingService;

    private PushReceiptScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PushReceiptScheduler(expoPushClient, pushTicketTracker, pushDeviceService, loggingService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenDue(Map<String, String> due) {
        when(expoPushClient.isEnabled()).thenReturn(true);
        when(pushTicketTracker.expire(NOW)).thenReturn(0);
        when(pushTicketTracker.due(NOW)).thenReturn(due);
    }

    @Test
    void checkReceipts_sendingOff_doesNothing() {
        // Arrange
        when(expoPushClient.isEnabled()).thenReturn(false);

        // Act
        scheduler.checkReceipts();

        // Assert
        verifyNoInteractions(pushTicketTracker, pushDeviceService);
    }

    @Test
    void checkReceipts_nothingDue_readsNoReceipts() {
        // Arrange
        givenDue(Map.of());

        // Act
        scheduler.checkReceipts();

        // Assert
        verify(expoPushClient, never()).getReceipts(anyList());
    }

    @Test
    void checkReceipts_deviceNotRegisteredReceipt_removesTheTokenAndForgetsTheTicket() {
        // Arrange
        Map<String, String> due = new LinkedHashMap<>();
        due.put("t1", "ExponentPushToken[dead]");
        due.put("t2", "ExponentPushToken[live]");
        givenDue(due);
        when(expoPushClient.getReceipts(List.of("t1", "t2"))).thenReturn(Map.of(
                "t1", new ExpoPushReceipt("error", "gone", new ExpoPushErrorDetails("DeviceNotRegistered")),
                "t2", new ExpoPushReceipt("ok", null, null)));

        // Act
        scheduler.checkReceipts();

        // Assert
        verify(pushDeviceService).removeTokens(List.of("ExponentPushToken[dead]"));
        verify(pushTicketTracker).forget(List.of("t1", "t2"));
    }

    @Test
    void checkReceipts_otherReceiptError_warnsAndKeepsTheDevice() {
        // Arrange
        givenDue(Map.of("t1", "ExponentPushToken[a]"));
        when(expoPushClient.getReceipts(List.of("t1"))).thenReturn(Map.of(
                "t1", new ExpoPushReceipt("error", "x", new ExpoPushErrorDetails("MessageTooBig"))));

        // Act
        scheduler.checkReceipts();

        // Assert
        verify(pushDeviceService, never()).removeTokens(anyCollection());
        verify(loggingService).warn(any(), eq(LogLayer.SCHEDULER),
                eq(PushReceiptSchedulerLogConstant.RECEIPT_ERROR), any(Object[].class));
    }

    @Test
    void checkReceipts_ticketWithoutReceiptYet_staysTracked() {
        // Arrange
        givenDue(Map.of("t1", "ExponentPushToken[a]"));
        when(expoPushClient.getReceipts(List.of("t1"))).thenReturn(Map.of());

        // Act
        scheduler.checkReceipts();

        // Assert
        verify(pushTicketTracker).forget(List.of());
        verify(pushDeviceService, never()).removeTokens(anyCollection());
    }

    @Test
    void checkReceipts_expoUnavailable_stopsAtTheFirstChunkAndLogs() {
        // Arrange
        givenDue(Map.of("t1", "ExponentPushToken[a]"));
        BadGatewayException unavailable = new BadGatewayException(ExceptionDomainPushError.UPS_EXPO_UNAVAILABLE);
        when(expoPushClient.getReceipts(anyList())).thenThrow(unavailable);

        // Act and assert
        assertThatCode(() -> scheduler.checkReceipts()).doesNotThrowAnyException();
        verify(expoPushClient, times(1)).getReceipts(anyList());
        verify(loggingService).error(any(), eq(LogLayer.SCHEDULER),
                eq(PushReceiptSchedulerLogConstant.RECEIPT_CHUNK_FAILED), eq(unavailable), any(Object[].class));
        verify(pushTicketTracker, never()).forget(anyCollection());
    }

    @Test
    void checkReceipts_tokenRemovalFails_isLoggedAndDoesNotThrow() {
        // Arrange
        givenDue(Map.of("t1", "ExponentPushToken[dead]"));
        when(expoPushClient.getReceipts(List.of("t1"))).thenReturn(Map.of(
                "t1", new ExpoPushReceipt("error", "gone", new ExpoPushErrorDetails("DeviceNotRegistered"))));
        RuntimeException failure = new IllegalStateException("database down");
        doThrow(failure).when(pushDeviceService).removeTokens(anyCollection());

        // Act and assert
        assertThatCode(() -> scheduler.checkReceipts()).doesNotThrowAnyException();
        verify(loggingService).error(any(), eq(LogLayer.SCHEDULER),
                eq(PushReceiptSchedulerLogConstant.TOKEN_REMOVAL_FAILED), eq(failure), any(Object[].class));
    }
}
