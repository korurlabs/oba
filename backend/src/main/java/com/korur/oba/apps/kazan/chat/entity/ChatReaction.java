package com.korur.oba.apps.kazan.chat.entity;

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
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing one user's emoji reaction to a chat message.
 *
 * One row per message, user and emoji. Reactions are added and removed
 * independently of the message, so the message is a plain ID column and the
 * reactions of a page are read in bulk through their repository.
 */
@Entity
@Table(name = "chat_reactions", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatReaction extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * The emoji as its Unicode character sequence.
     */
    @Column(name = "emoji", length = 64, nullable = false)
    private String emoji;
}
