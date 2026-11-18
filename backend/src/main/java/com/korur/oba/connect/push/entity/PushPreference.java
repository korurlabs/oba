package com.korur.oba.connect.push.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * The switches that decide which pushes one user receives, one row per user and shared by all of the
 * user's devices.
 *
 * A user without a row has every switch on, so a user who never opened the settings still receives
 * mentions and urgent incidents. The row is removed with the user.
 */
@Entity
@Table(name = "push_preferences", schema = "shrine")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PushPreference extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Direct mentions, replies to the user's messages, review requests and daily-update nudges.
     */
    @Builder.Default
    @Column(name = "mentions_and_replies", nullable = false)
    private Boolean mentionsAndReplies = true;

    /**
     * Messages for everyone in a channel the user reads.
     */
    @Builder.Default
    @Column(name = "everyone", nullable = false)
    private Boolean everyone = true;

    /**
     * Messages for everyone online in a channel the user reads.
     */
    @Builder.Default
    @Column(name = "here", nullable = false)
    private Boolean here = true;

    /**
     * Incidents opened or raised to a high priority on the user's boards.
     */
    @Builder.Default
    @Column(name = "urgent_incidents", nullable = false)
    private Boolean urgentIncidents = true;

    /**
     * Whether a push shows the message text; when off it only names the conversation, so the text
     * never appears on a locked screen.
     */
    @Builder.Default
    @Column(name = "show_previews", nullable = false)
    private Boolean showPreviews = true;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
}
