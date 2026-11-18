package com.korur.oba.apps.kazan.chat.repository.integration;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 2. Third-party
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.support.ObaIntegrationTest;

/**
 * Runs the four channel page queries against the Flyway-migrated PostgreSQL schema,
 * because how Hibernate binds the boolean "any status" flag and the expanded status list in a
 * native query, and the EXISTS clause on the pull request share, cannot be checked on a mock.
 *
 * Each test creates its own organization, users, channel, shares and messages. The cleanup
 * removes the organization, which removes everything under it, and then the users, so a
 * database shared with a running backend is not disturbed.
 */
class ChatMessageRepositoryIT extends ObaIntegrationTest {

    private static final List<String> ALL_STATUSES = List.of("DRAFT", "OPEN", "IN_REVIEW", "MERGED", "DISCUSS");
    private static final Instant BASE = Instant.parse("2026-10-20T07:00:00Z");

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> organizationIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    private UUID organizationId;
    private UUID channelId;
    private UUID alice;
    private UUID bob;

    /**
     * Channel entries, oldest first: the id of each message by what it stands for.
     */
    private UUID openByAlice;
    private UUID inReviewByBob;
    private UUID mergedByAlice;
    private UUID plainByAlice;
    private UUID deletedShareByAlice;
    private UUID openByBob;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.organizations (id, name, status) VALUES (?, ?, 'ACTIVE')",
                organizationId, "itest " + organizationId);
        organizationIds.add(organizationId);
        alice = insertUser("alice");
        bob = insertUser("bob");
        channelId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.chat_channels (id, name, slug, type, purpose, visibility, organization_id)
                VALUES (?, 'PR', 'pr', 'TEXT', 'PULL_REQUESTS', 'PUBLIC', ?)
                """, channelId, organizationId);

        openByAlice = insertShareEntry(alice, "OPEN", false, 1);
        inReviewByBob = insertShareEntry(bob, "IN_REVIEW", false, 2);
        mergedByAlice = insertShareEntry(alice, "MERGED", false, 3);
        plainByAlice = insertPlainMessage(alice, 4);
        deletedShareByAlice = insertShareEntry(alice, "OPEN", true, 5);
        openByBob = insertShareEntry(bob, "OPEN", false, 6);
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : organizationIds) {
            jdbc.update("DELETE FROM shrine.organizations WHERE id = ?", id);
        }
        for (UUID id : userIds) {
            jdbc.update("DELETE FROM shrine.users WHERE id = ?", id);
        }
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.users (id, email, first_name, last_name) VALUES (?, ?, ?, ?)",
                id, name + "-" + id + "@itest.example", name, "itest");
        userIds.add(id);
        return id;
    }

    private UUID insertShareEntry(UUID authorId, String status, boolean shareDeleted, int minute) {
        UUID shareId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.pull_request_shares (id, organization_id, author_id, github_repository_id,
                    repository_full_name, number, title, github_author_login, head_ref, base_ref, status, deleted)
                VALUES (?, ?, ?, 1, 'acme/app', ?, 'A change', 'octo', 'feature', 'main',
                    CAST(? AS kazan_pull_request_share_status_enum), ?)
                """, shareId, organizationId, authorId, minute, status, shareDeleted);
        UUID messageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.chat_messages (id, channel_id, organization_id, author_id, pull_request_share_id,
                    created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, messageId, channelId, organizationId, authorId, shareId,
                java.sql.Timestamp.from(BASE.plusSeconds(60L * minute)));
        return messageId;
    }

    private UUID insertPlainMessage(UUID authorId, int minute) {
        UUID messageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.chat_messages (id, channel_id, organization_id, author_id, content, created_at)
                VALUES (?, ?, ?, ?, 'hello', ?)
                """, messageId, channelId, organizationId, authorId,
                java.sql.Timestamp.from(BASE.plusSeconds(60L * minute)));
        return messageId;
    }

    private static List<UUID> ids(List<ChatMessage> messages) {
        return messages.stream().map(ChatMessage::getId).toList();
    }

    private List<ChatMessage> latest(UUID authorId, boolean any, List<String> statuses) {
        return chatMessageRepository.findLatestChannelMessages(channelId, null, null, authorId, any, statuses, 50);
    }

    @Test
    void findLatestChannelMessages_noStatusFilter_keepsEveryLiveMessageWhateverItsShare() {
        List<ChatMessage> result = latest(null, true, ALL_STATUSES);

        assertThat(ids(result)).containsExactly(openByAlice, inReviewByBob, mergedByAlice, plainByAlice,
                deletedShareByAlice, openByBob);
    }

    @Test
    void findLatestChannelMessages_statusFilter_keepsMatchingSharesOnly() {
        List<ChatMessage> result = latest(null, false, List.of("OPEN", "IN_REVIEW"));

        assertThat(ids(result)).containsExactly(openByAlice, inReviewByBob, openByBob);
    }

    @Test
    void findLatestChannelMessages_singleStatus_keepsThatStatusOnly() {
        List<ChatMessage> result = latest(null, false, List.of("MERGED"));

        assertThat(ids(result)).containsExactly(mergedByAlice);
    }

    @Test
    void findLatestChannelMessages_statusWithoutAnyShare_returnsEmpty() {
        List<ChatMessage> result = latest(null, false, List.of("DRAFT", "DISCUSS"));

        assertThat(result).isEmpty();
    }

    @Test
    void findLatestChannelMessages_statusFilter_dropsPlainMessagesAndDeletedShares() {
        List<ChatMessage> result = latest(null, false, ALL_STATUSES);

        assertThat(ids(result)).doesNotContain(plainByAlice, deletedShareByAlice);
        assertThat(result).hasSize(4);
    }

    @Test
    void findLatestChannelMessages_statusAndAuthor_combineWithAnd() {
        List<ChatMessage> result = latest(alice, false, List.of("OPEN", "MERGED"));

        assertThat(ids(result)).containsExactly(openByAlice, mergedByAlice);
    }

    @Test
    void findLatestChannelMessages_deletedThreadRootOfAShare_isDroppedByAStatusFilter() {
        UUID threadId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO kazan.chat_threads (id, title, root_message_id, last_message_at, channel_id,
                    organization_id, created_by_id)
                VALUES (?, 'thread', ?, now(), ?, ?, ?)
                """, threadId, plainByAlice, channelId, organizationId, alice);
        jdbc.update("UPDATE kazan.chat_messages SET deleted = true WHERE id = ?", plainByAlice);

        assertThat(ids(latest(null, true, ALL_STATUSES))).contains(plainByAlice);
        assertThat(ids(latest(null, false, ALL_STATUSES))).doesNotContain(plainByAlice);
    }

    @Test
    void findChannelMessagesBefore_statusFilter_keepsTheFilter() {
        List<ChatMessage> result = chatMessageRepository.findChannelMessagesBefore(channelId, null, null, null,
                false, List.of("OPEN", "MERGED"), BASE.plusSeconds(60L * 6), openByBob, 50);

        assertThat(ids(result)).containsExactly(openByAlice, mergedByAlice);
    }

    @Test
    void findChannelMessagesAfter_statusFilter_keepsTheFilter() {
        List<ChatMessage> result = chatMessageRepository.findChannelMessagesAfter(channelId, null, null, null,
                false, List.of("OPEN", "MERGED"), BASE.plusSeconds(60L * 1), openByAlice, 50);

        assertThat(ids(result)).containsExactly(mergedByAlice, openByBob);
    }

    @Test
    void findChannelMessagesAround_statusFilter_keepsTheFilterOnBothSides() {
        List<ChatMessage> result = chatMessageRepository.findChannelMessagesAround(channelId, null, null, null,
                false, List.of("OPEN", "IN_REVIEW"), BASE.plusSeconds(60L * 2), inReviewByBob, 10, 10);

        assertThat(ids(result)).containsExactly(openByAlice, inReviewByBob, openByBob);
    }
}
