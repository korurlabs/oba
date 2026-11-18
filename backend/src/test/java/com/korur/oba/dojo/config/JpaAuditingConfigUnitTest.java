package com.korur.oba.dojo.config;

// 1. Framework-level
import org.springframework.data.auditing.DateTimeProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class JpaAuditingConfigUnitTest {

    @Test
    void auditingDateTimeProvider_fixedClock_returnsClockInstant() {
        // Arrange
        Instant fixedNow = Instant.parse("2026-11-03T21:30:00Z");
        Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        DateTimeProvider provider = new JpaAuditingConfig().auditingDateTimeProvider(clock);

        // Act + Assert
        assertThat(provider.getNow()).contains(fixedNow);
    }
}
