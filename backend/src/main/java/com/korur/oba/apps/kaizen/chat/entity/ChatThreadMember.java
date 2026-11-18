package com.korur.oba.apps.kaizen.chat.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing a user who follows a thread.
 *
 * createdAt is the join time: for a member without a read state of the
 * thread, replies after it count as unread. One row per thread and user.
 */
@Entity
@Table(name = "chat_thread_members", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatThreadMember extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // -------------Relations-----------//

    /**
     * Thread the user follows.
     *
     * No cascade -- the database removes members with their thread.
     *
     * Fetch: LAZY -- member lookups need only the thread ID.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private ChatThread thread;
}
