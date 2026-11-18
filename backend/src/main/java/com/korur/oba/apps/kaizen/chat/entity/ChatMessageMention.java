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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing one user mentioned by a chat message.
 *
 * Derived from the tokens in the message content, so it is never edited on
 * its own. One row per message and user: a user mentioned both directly and
 * through @here gets one row with viaHere false.
 */
@Entity
@Table(name = "chat_message_mentions", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageMention extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * True when the user was reached only through @here, being online when
     * the message was sent.
     */
    @Builder.Default
    @Column(name = "via_here", nullable = false)
    private boolean viaHere = false;

    // -------------Relations-----------//

    /**
     * Message that mentions the user.
     *
     * No cascade -- the mention is owned by {@link ChatMessage#mentions}.
     *
     * Fetch: EAGER -- Hibernate 6 does not support a LAZY to-one association to
     * an entity that uses @SoftDelete.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;
}
