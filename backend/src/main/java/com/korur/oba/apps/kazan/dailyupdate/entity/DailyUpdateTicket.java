package com.korur.oba.apps.kazan.dailyupdate.entity;

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
 * Entity representing one ticket line of a daily update.
 *
 * The line is a snapshot: key, title, board ID and board name are copied when
 * the line is added and never follow the ticket or board afterwards, so the
 * update reads the same after a rename and survives the ticket's deletion.
 * The ticket and board are therefore plain ID columns rather than relations;
 * the database clears ticket_id when the ticket row is removed.
 */
@Entity
@Table(name = "daily_update_tickets", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DailyUpdateTicket extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Ticket the line was written for; null once the ticket row is removed.
     */
    @Column(name = "ticket_id", nullable = true)
    private UUID ticketId;

    /**
     * Board the ticket was on when the line was added.
     */
    @Column(name = "board_id", nullable = false)
    private UUID boardId;

    @Column(name = "ticket_key", length = 50, nullable = false)
    private String ticketKey;

    @Column(name = "ticket_title", length = 500, nullable = false)
    private String ticketTitle;

    @Column(name = "board_name", length = 255, nullable = false)
    private String boardName;

    /**
     * Progress the author reported, 0 to 100.
     */
    @Column(name = "percentage", nullable = false)
    private Integer percentage;

    /**
     * Optional note shown under the ticket line; null when left empty.
     */
    @Column(name = "note", length = 500, nullable = true)
    private String note;

    /**
     * True once this line has written its percentage back to the ticket; it
     * stays true after later edits.
     */
    @Builder.Default
    @Column(name = "progress_synced", nullable = false)
    private boolean progressSynced = false;

    /**
     * Zero-based place of the line in the update.
     */
    @Column(name = "position", nullable = false)
    private Integer position;

    // -------------Relations-----------//

    /**
     * Update this line belongs to.
     *
     * No cascade -- the line is owned by DailyUpdate.items.
     *
     * Fetch: EAGER -- Hibernate 6 does not support a LAZY to-one association to
     * an entity that uses @SoftDelete.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "daily_update_id", nullable = false)
    private DailyUpdate dailyUpdate;
}
