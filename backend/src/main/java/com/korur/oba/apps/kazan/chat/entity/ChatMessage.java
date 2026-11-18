package com.korur.oba.apps.kazan.chat.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Entity representing one message of a chat channel, in the channel itself or
 * in one of its threads.
 *
 * Every message belongs to one channel; a thread message carries its thread's
 * channel, so channel access is checked on the message alone. A daily update
 * entry is an ordinary message with dailyUpdateId set, a pull request share
 * entry one with pullRequestShareId set.
 * The author or a manager deletes a message softly; its thread, reactions and
 * replies stay. Mentions and ticket references are derived from the tokens in
 * content and replaced on every create and edit. Reactions change outside the
 * message's lifecycle, so they are read through their own repository and are
 * no collection here.
 */
@Entity
@Table(name = "chat_messages", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class ChatMessage extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    /**
     * Organization whose chat holds the message.
     */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /**
     * Channel that holds the message; for a thread message the thread's channel.
     */
    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    /**
     * Message text with its mention and ticket tokens. Empty only when the
     * message carries attachments, a daily update or a pull request share,
     * whose text is read from the update or the share.
     */
    @Builder.Default
    @Column(name = "content", length = 2000, nullable = false)
    private String content = "";

    /**
     * Daily update this message stands for in the channel; null for every
     * other message. At most one message per update. A plain ID column, read
     * through the daily update domain, as
     * {@link com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdateTicket#ticketId}.
     */
    @Column(name = "daily_update_id", nullable = true, unique = true)
    private UUID dailyUpdateId;

    /**
     * Pull request share this message stands for in the pull requests
     * channel; null for every other message. At most one message per share,
     * never in a thread and never beside a daily update. A plain ID column,
     * read through the pull request domain, as dailyUpdateId.
     */
    @Column(name = "pull_request_share_id", nullable = true, unique = true)
    private UUID pullRequestShareId;

    /**
     * Message this one replies to; null when it is no reply. A plain ID column,
     * so a reply still loads when its target is gone. The database clears it
     * when the target row is removed.
     */
    @Column(name = "reply_to_message_id", nullable = true)
    private UUID replyToMessageId;

    /**
     * True when the message mentions every member of the organization.
     */
    @Builder.Default
    @Column(name = "mentions_everyone", nullable = false)
    private boolean mentionsEveryone = false;

    /**
     * True when the message mentions the members online at send time.
     */
    @Builder.Default
    @Column(name = "mentions_here", nullable = false)
    private boolean mentionsHere = false;

    /**
     * Time of the author's last edit; null until the first edit.
     */
    @Column(name = "edited_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = true)
    private Instant editedAt;

    /**
     * Time the message was pinned; null when it is not pinned.
     */
    @Column(name = "pinned_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = true)
    private Instant pinnedAt;

    /**
     * User who pinned the message; null when it is not pinned.
     */
    @Column(name = "pinned_by_id", nullable = true)
    private UUID pinnedBy;

    /**
     * Key the author's client generated for the send that created the message,
     * so a send repeated after a lost response finds this row instead of
     * storing a copy. Unique per author while set, deleted rows included; null
     * for the entries written before the client sent one.
     */
    @Column(name = "client_nonce", length = ChatConstant.MAX_CLIENT_NONCE_LENGTH, nullable = true)
    private String clientNonce;

    // -------------Relations-----------//

    /**
     * Thread the message was posted in; null for a channel message.
     *
     * No cascade -- the thread is managed on its own; the database removes a
     * thread's messages with the thread.
     *
     * Fetch: LAZY -- a channel page needs no thread of its messages.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "thread_id", nullable = true)
    private ChatThread thread;

    /**
     * User who wrote the message; only this user may edit it.
     *
     * No cascade -- user lifecycle is independent of the message.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /**
     * Users mentioned by the message, directly or through @here.
     *
     * Cascade: ALL + orphanRemoval -- a mention exists only inside its message
     * and is replaced through this collection on every edit.
     *
     * Batch size: the mentions of a whole page load in one query.
     */
    @Builder.Default
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessageMention> mentions = new ArrayList<>();

    /**
     * Tickets referenced by the message, in the order they appear in content.
     *
     * Cascade: ALL + orphanRemoval -- a reference exists only inside its
     * message and is replaced through this collection on every edit.
     *
     * Batch size: the references of a whole page load in one query.
     */
    @Builder.Default
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ChatMessageTicketRef> ticketRefs = new ArrayList<>();
}
