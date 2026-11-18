package com.korur.oba.apps.kazan.chat.mock_data;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.chat.dto.projection.ChatReactionSummaryProjection;
import com.korur.oba.apps.kazan.chat.dto.projection.ChatReplyPreviewProjection;
import com.korur.oba.apps.kazan.chat.dto.projection.ChatThreadSummaryProjection;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatMessageMention;
import com.korur.oba.apps.kazan.chat.entity.ChatMessageTicketRef;
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;

/**
 * Builders for the entities and projections the chat tests share.
 */
public final class ChatMocks {

    private ChatMocks() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static User user(String firstName, String lastName) {
        User user = User.builder()
                .email(firstName.toLowerCase() + "@example.com")
                .profile(UserProfile.builder().firstName(firstName).lastName(lastName).build())
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }

    public static ChatMessage message(User author, UUID organizationId, String content) {
        ChatMessage message = ChatMessage.builder()
                .organizationId(organizationId)
                .channelId(UUID.randomUUID())
                .author(author)
                .content(content)
                .build();
        message.setId(UUID.randomUUID());
        message.setCreatedAt(Instant.parse("2026-10-20T07:30:00Z"));
        return message;
    }

    public static ChatMessage dailyUpdateMessage(User author, UUID organizationId, UUID dailyUpdateId) {
        ChatMessage message = message(author, organizationId, "");
        message.setDailyUpdateId(dailyUpdateId);
        return message;
    }

    public static ChatMessage pullRequestShareMessage(User author, UUID organizationId, UUID pullRequestShareId) {
        ChatMessage message = message(author, organizationId, "");
        message.setPullRequestShareId(pullRequestShareId);
        return message;
    }

    public static ChatThread thread(UUID organizationId, UUID rootMessageId, UUID createdBy) {
        ChatThread thread = ChatThread.builder()
                .organizationId(organizationId)
                .channelId(UUID.randomUUID())
                .rootMessageId(rootMessageId)
                .createdBy(createdBy)
                .title("Release checklist")
                .lastMessageAt(Instant.now())
                .build();
        thread.setId(UUID.randomUUID());
        return thread;
    }

    public static Ticket ticket(String key, String title) {
        Board board = Board.builder().name("Board").prefix("B").color("#112233").build();
        board.setId(UUID.randomUUID());
        Ticket ticket = Ticket.builder().title(title).key(key).type(TicketType.TASK).board(board).build();
        ticket.setId(UUID.randomUUID());
        return ticket;
    }

    public static ChatMessageTicketRef ticketRef(ChatMessage message, Ticket ticket, int position) {
        ChatMessageTicketRef ticketRef = ChatMessageTicketRef.builder()
                .message(message)
                .ticketId(ticket.getId())
                .boardId(ticket.getBoard().getId())
                .ticketKey(ticket.getKey())
                .ticketTitle(ticket.getTitle())
                .position(position)
                .build();
        ticketRef.setId(UUID.randomUUID());
        message.getTicketRefs().add(ticketRef);
        return ticketRef;
    }

    public static ChatMessageMention mention(ChatMessage message, UUID userId, boolean viaHere) {
        ChatMessageMention mention = ChatMessageMention.builder()
                .message(message)
                .userId(userId)
                .viaHere(viaHere)
                .build();
        mention.setId(UUID.randomUUID());
        message.getMentions().add(mention);
        return mention;
    }

    public static String userToken(UUID userId) {
        return "<@" + userId + ">";
    }

    public static String ticketToken(UUID ticketId) {
        return "<#" + ticketId + ">";
    }

    public static OrganizationMemberSummaryProjection member(User user) {
        return new MemberSummary(user.getId(), user.getEmail(), user.getProfile().getFirstName(),
                user.getProfile().getLastName(), "MEMBER");
    }

    public static ChatReplyPreviewProjection replyPreview(ChatMessage target, boolean deleted) {
        return new ReplyPreview(target.getId(), target.getAuthor().getId(), target.getContent(),
                target.getDailyUpdateId(), target.getPullRequestShareId(), deleted);
    }

    public static ChatReactionSummaryProjection reaction(UUID messageId, String emoji, UUID... userIds) {
        return new ReactionSummary(messageId, emoji, userIds.length, false, userIds, Instant.now());
    }

    public static ChatThreadSummaryProjection threadSummary(UUID rootMessageId, UUID... memberPreviewIds) {
        return new ThreadSummary(UUID.randomUUID(), rootMessageId, "Release checklist", 3, Instant.now(), 10080,
                ChatThreadStatus.ACTIVE.name(), UUID.randomUUID(), memberPreviewIds.length, memberPreviewIds);
    }

    private record MemberSummary(UUID getUserId, String getEmail, String getFirstName, String getLastName,
                                 String getRole) implements OrganizationMemberSummaryProjection {
    }

    private record ReplyPreview(UUID getId, UUID getAuthorId, String getContent, UUID getDailyUpdateId,
                                UUID getPullRequestShareId, Boolean getDeleted)
            implements ChatReplyPreviewProjection {
    }

    private record ReactionSummary(UUID getMessageId, String getEmoji, Integer getReactionCount,
                                   Boolean getReactedByMe, UUID[] getUserIds, Instant getFirstReactedAt)
            implements ChatReactionSummaryProjection {
    }

    private record ThreadSummary(UUID getThreadId, UUID getRootMessageId, String getTitle, Integer getMessageCount,
                                 Instant getLastMessageAt, Integer getAutoArchiveMinutes, String getStatus,
                                 UUID getCreatedBy, Integer getMemberCount, UUID[] getMemberPreviewIds)
            implements ChatThreadSummaryProjection {
    }
}
