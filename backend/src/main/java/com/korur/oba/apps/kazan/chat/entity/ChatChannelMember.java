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
 * Entity representing a user on the member list of a private channel.
 *
 * Public channels have no rows, because every member of the organization
 * reads them. The channel is a plain ID column rather than a relation: a
 * to-one relation to the soft-deleted {@link ChatChannel} would have to load
 * eagerly, and member lookups only need the ID. createdAt is the join time.
 * One row per channel and user.
 */
@Entity
@Table(name = "chat_channel_members", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatChannelMember extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
}
