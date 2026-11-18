package com.korur.oba.connect.realtime.service;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 2. Third-party

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.service.RealtimeTicketStore.RealtimeTicketEntry;

class InMemoryRealtimeTicketStoreUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private final UUID userId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private InMemoryRealtimeTicketStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryRealtimeTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RealtimeTicketEntry entry(UUID user, UUID organization, Instant ticketExpiresAt) {
        return new RealtimeTicketEntry(new RealtimePrincipal(user, organization, NOW.plusSeconds(300)), ticketExpiresAt);
    }

    @Test
    void take_savedTicket_returnsItOnceThenNothing() {
        // Arrange
        RealtimeTicketEntry saved = entry(userId, organizationId, NOW.plusSeconds(30));
        store.save("ticket-1", saved);

        // Act
        Optional<RealtimeTicketEntry> first = store.take("ticket-1");
        Optional<RealtimeTicketEntry> second = store.take("ticket-1");

        // Assert
        assertThat(first).contains(saved);
        assertThat(second).isEmpty();
    }

    @Test
    void take_unknownTicket_returnsEmpty() {
        assertThat(store.take("never-issued")).isEmpty();
    }

    @Test
    void take_expiredTicket_isPurgedAndReturnsEmpty() {
        // Arrange
        store.save("expired", entry(userId, organizationId, NOW));

        // Act and Assert
        assertThat(store.take("expired")).isEmpty();
    }

    @Test
    void countLive_countsOnlyUnexpiredTicketsOfThatUser() {
        // Arrange
        store.save("live-1", entry(userId, organizationId, NOW.plusSeconds(30)));
        store.save("live-2", entry(userId, UUID.randomUUID(), NOW.plusSeconds(30)));
        store.save("expired", entry(userId, organizationId, NOW.minusSeconds(1)));
        store.save("other-user", entry(UUID.randomUUID(), organizationId, NOW.plusSeconds(30)));

        // Act and Assert
        assertThat(store.countLive(userId, NOW)).isEqualTo(2);
    }

    @Test
    void removeMatching_membership_dropsOnlyThatUsersTicketsInThatOrganization() {
        // Arrange
        UUID otherOrganizationId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        store.save("revoked", entry(userId, organizationId, NOW.plusSeconds(30)));
        store.save("other-organization", entry(userId, otherOrganizationId, NOW.plusSeconds(30)));
        store.save("other-user", entry(otherUserId, organizationId, NOW.plusSeconds(30)));

        // Act
        store.removeMatching(userId, organizationId);

        // Assert
        assertThat(store.take("revoked")).isEmpty();
        assertThat(store.take("other-organization")).isPresent();
        assertThat(store.take("other-user")).isPresent();
    }

    @Test
    void removeMatching_nullOrganization_dropsTheUsersTicketsEverywhere() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        store.save("first", entry(userId, organizationId, NOW.plusSeconds(30)));
        store.save("second", entry(userId, UUID.randomUUID(), NOW.plusSeconds(30)));
        store.save("other-user", entry(otherUserId, organizationId, NOW.plusSeconds(30)));

        // Act
        store.removeMatching(userId, null);

        // Assert
        assertThat(store.countLive(userId, NOW)).isZero();
        assertThat(store.take("other-user")).isPresent();
    }

    @Test
    void removeMatching_nullUser_dropsEveryTicketOfTheOrganization() {
        // Arrange
        UUID otherOrganizationId = UUID.randomUUID();
        store.save("first", entry(userId, organizationId, NOW.plusSeconds(30)));
        store.save("second", entry(UUID.randomUUID(), organizationId, NOW.plusSeconds(30)));
        store.save("other-organization", entry(userId, otherOrganizationId, NOW.plusSeconds(30)));

        // Act
        store.removeMatching(null, organizationId);

        // Assert
        assertThat(store.take("first")).isEmpty();
        assertThat(store.take("second")).isEmpty();
        assertThat(store.take("other-organization")).isPresent();
    }
}
