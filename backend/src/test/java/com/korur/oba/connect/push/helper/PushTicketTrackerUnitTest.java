package com.korur.oba.connect.push.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 3. Java SDK
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.constant.helper.PushTicketTrackerLogConstant;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;

class PushTicketTrackerUnitTest {

    private static final Instant SENT_AT = Instant.parse("2026-11-06T09:00:00Z");

    private final LoggingService loggingService = mock(LoggingService.class);
    private final PushTicketTracker tracker = new PushTicketTracker(loggingService);

    @Test
    void due_ticketYoungerThanTheReceiptDelay_isNotDue() {
        // Arrange
        tracker.track(Map.of("t1", "ExponentPushToken[a]"), SENT_AT);

        // Act and assert
        assertThat(tracker.due(SENT_AT.plus(PushConstant.RECEIPT_DELAY).minusSeconds(1))).isEmpty();
    }

    @Test
    void due_ticketAtTheReceiptDelay_isDueWithItsToken() {
        // Arrange
        tracker.track(Map.of("t1", "ExponentPushToken[a]"), SENT_AT);

        // Act and assert
        assertThat(tracker.due(SENT_AT.plus(PushConstant.RECEIPT_DELAY)))
                .containsExactly(Map.entry("t1", "ExponentPushToken[a]"));
    }

    @Test
    void forget_removesTheTicketsSoTheyAreNoLongerDue() {
        // Arrange
        tracker.track(Map.of("t1", "a", "t2", "b"), SENT_AT);

        // Act
        tracker.forget(List.of("t1"));

        // Assert
        assertThat(tracker.due(SENT_AT.plus(PushConstant.RECEIPT_RETENTION))).containsOnlyKeys("t2");
    }

    @Test
    void expire_dropsTicketsOlderThanTheRetentionOnly() {
        // Arrange
        tracker.track(Map.of("old", "a"), SENT_AT);
        tracker.track(Map.of("new", "b"), SENT_AT.plus(PushConstant.RECEIPT_RETENTION));
        Instant now = SENT_AT.plus(PushConstant.RECEIPT_RETENTION).plusSeconds(1);

        // Act
        int expired = tracker.expire(now);

        // Assert
        assertThat(expired).isEqualTo(1);
        assertThat(tracker.due(now.plus(PushConstant.RECEIPT_DELAY))).containsOnlyKeys("new");
    }

    @Test
    void track_beyondTheLimit_dropsTheExtraTicketsAndWarns() {
        // Arrange
        Map<String, String> first = new HashMap<>();
        for (int index = 0; index < PushConstant.RECEIPT_TRACKING_LIMIT; index++) {
            first.put("t" + index, "token");
        }
        tracker.track(first, SENT_AT);
        verify(loggingService, never()).warn(any(), any(), any(String.class), any(Object[].class));

        // Act
        tracker.track(Map.of("extra", "token"), SENT_AT);

        // Assert
        assertThat(tracker.due(SENT_AT.plus(PushConstant.RECEIPT_DELAY))).doesNotContainKey("extra");
        verify(loggingService).warn(any(), eq(LogLayer.HELPER),
                eq(PushTicketTrackerLogConstant.TRACKING_LIMIT_REACHED), any(Object[].class));
    }
}
