package com.korur.oba.apps.kazan.chat.helper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;

class ChatThreadStatusResolverUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private final ChatThreadStatusResolver resolver = new ChatThreadStatusResolver();

    private ChatThread thread(ChatThreadStatus status, Duration sinceLastReply) {
        ChatThread thread = ChatMocks.thread(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        thread.setAutoArchiveMinutes(60);
        thread.setLastMessageAt(NOW.minus(sinceLastReply));
        thread.setStatus(status);
        return thread;
    }

    @Nested
    class Resolve {

        @Test
        void activeWithinWindow_isActive() {
            ChatThread thread = thread(ChatThreadStatus.ACTIVE, Duration.ofMinutes(59));

            assertThat(resolver.resolve(thread, NOW)).isEqualTo(ChatThreadStatus.ACTIVE);
        }

        @Test
        void activeAtEndOfWindow_isArchived() {
            ChatThread thread = thread(ChatThreadStatus.ACTIVE, Duration.ofMinutes(60));

            assertThat(resolver.resolve(thread, NOW)).isEqualTo(ChatThreadStatus.ARCHIVED);
        }

        @Test
        void activeQuietButStatusChangedWithinWindow_isActive() {
            ChatThread thread = thread(ChatThreadStatus.ACTIVE, Duration.ofDays(10));
            thread.setStatusChangedAt(NOW.minus(Duration.ofMinutes(5)));

            assertThat(resolver.resolve(thread, NOW)).isEqualTo(ChatThreadStatus.ACTIVE);
        }

        @Test
        void closedAndQuiet_staysClosed() {
            ChatThread thread = thread(ChatThreadStatus.CLOSED, Duration.ofDays(10));

            assertThat(resolver.resolve(thread, NOW)).isEqualTo(ChatThreadStatus.CLOSED);
        }

        @Test
        void archivedByHandWithRecentReply_staysArchived() {
            ChatThread thread = thread(ChatThreadStatus.ARCHIVED, Duration.ofMinutes(1));

            assertThat(resolver.resolve(thread, NOW)).isEqualTo(ChatThreadStatus.ARCHIVED);
        }
    }

    @Nested
    class ChangedAtAndBy {

        @Test
        void archivedByInactivity_isEndOfWindowWithoutUser() {
            ChatThread thread = thread(ChatThreadStatus.ACTIVE, Duration.ofDays(1));
            Instant reopenedAt = NOW.minus(Duration.ofHours(2));
            thread.setStatusChangedAt(reopenedAt);
            thread.setStatusChangedBy(UUID.randomUUID());

            assertThat(resolver.resolveChangedAt(thread, NOW)).isEqualTo(reopenedAt.plus(Duration.ofMinutes(60)));
            assertThat(resolver.resolveChangedBy(thread, NOW)).isNull();
        }

        @Test
        void closedByUser_isStoredChange() {
            ChatThread thread = thread(ChatThreadStatus.CLOSED, Duration.ofDays(1));
            Instant closedAt = NOW.minus(Duration.ofHours(5));
            UUID closedBy = UUID.randomUUID();
            thread.setStatusChangedAt(closedAt);
            thread.setStatusChangedBy(closedBy);

            assertThat(resolver.resolveChangedAt(thread, NOW)).isEqualTo(closedAt);
            assertThat(resolver.resolveChangedBy(thread, NOW)).isEqualTo(closedBy);
        }

        @Test
        void neverChangedAndActive_isNull() {
            ChatThread thread = thread(ChatThreadStatus.ACTIVE, Duration.ofMinutes(1));

            assertThat(resolver.resolveChangedAt(thread, NOW)).isNull();
            assertThat(resolver.resolveChangedBy(thread, NOW)).isNull();
        }
    }
}
