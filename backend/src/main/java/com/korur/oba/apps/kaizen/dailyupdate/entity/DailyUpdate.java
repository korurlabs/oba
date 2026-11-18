package com.korur.oba.apps.kaizen.dailyupdate.entity;

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
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Entity representing a daily status post written by one user: one line per
 * ticket plus free-text focus and questions.
 *
 * The update is not owned by a board. Its readers are the author's colleagues
 * in a shared organization, resolved at read time. organizationId only records
 * the chat channel the update was posted to. The author deletes it softly.
 */
@Entity
@Table(name = "daily_updates", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class DailyUpdate extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    /**
     * What the author will focus on today; null when left empty.
     */
    @Column(name = "focus", length = 2000, nullable = true)
    private String focus;

    /**
     * Questions to colleagues; null when left empty.
     */
    @Column(name = "questions", length = 2000, nullable = true)
    private String questions;

    /**
     * Time of the author's last edit; null until the first edit. Creation and
     * deletion never set it, so readers can tell an edited update apart.
     */
    @Column(name = "edited_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = true)
    private Instant editedAt;

    /**
     * Organization whose chat channel shows this update; null for an author
     * without an organization, and cleared when the organization row is
     * removed.
     */
    @Column(name = "organization_id", nullable = true)
    private UUID organizationId;

    // -------------Relations-----------//

    /**
     * User who wrote this update; only this user may edit or delete it.
     *
     * No cascade -- user lifecycle is independent of the update.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /**
     * Ticket lines of this update, in the order the author listed them.
     *
     * Cascade: ALL + orphanRemoval -- a line exists only inside its update, so
     * the author's edit adds and removes lines through this collection. A kept
     * line is changed in place: removing and re-adding a line for the same
     * ticket would insert before the orphan delete and break the unique key on
     * (daily_update_id, ticket_id).
     *
     * Batch size: the lines of a whole feed page load in one query when the
     * first update's lines are read, instead of one query per update.
     */
    @Builder.Default
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "dailyUpdate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<DailyUpdateTicket> items = new ArrayList<>();
}
