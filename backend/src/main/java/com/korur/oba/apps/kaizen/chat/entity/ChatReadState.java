package com.korur.oba.apps.kaizen.chat.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing how far a user has read one chat channel or one thread.
 *
 * One row per user and channel for the channel itself (threadId null) and one
 * per user and thread, enforced by partial unique indexes. A thread read
 * state also carries its thread's channel. Messages created
 * after lastReadAt count as unread. Read states created for existing members
 * when the chat was introduced carry their creation time rather than the
 * created_at of lastReadMessageId, so a read state ahead of its message means
 * the user is caught up.
 */
@Entity
@Table(name = "chat_read_states", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatReadState extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /**
     * Channel this read state is for, or the channel of its thread.
     */
    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    /**
     * Thread this read state is for; null for the channel itself.
     */
    @Column(name = "thread_id", nullable = true)
    private UUID threadId;

    /**
     * Last message the user has read; null when the channel or thread had no
     * message yet, or once that message row is removed. lastReadAt keeps the
     * position then.
     */
    @Column(name = "last_read_message_id", nullable = true)
    private UUID lastReadMessageId;

    /**
     * Read position in time; together with lastReadMessageId the keyset pair
     * that unread counts compare against.
     */
    @Column(name = "last_read_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant lastReadAt;
}
