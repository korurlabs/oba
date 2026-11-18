package com.korur.oba.apps.kaizen.pullrequest.entity;

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
 * Entity representing one ticket the author listed as related to a shared
 * pull request.
 *
 * The row is a snapshot: key, title, board ID and board name are copied when
 * the ticket is added and never follow the ticket or board afterwards, so the
 * card reads the same for every reader, after a rename and after the ticket is
 * deleted. The ticket and board are therefore plain ID columns rather than
 * relations, as in {@link com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdateTicket}.
 * A ticket is related to a share at most once.
 */
@Entity
@Table(name = "pull_request_share_tickets", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PullRequestShareTicket extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Ticket the author listed.
     */
    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    /**
     * Board the ticket was on when it was added.
     */
    @Column(name = "board_id", nullable = false, updatable = false)
    private UUID boardId;

    /**
     * Ticket key when the ticket was added.
     */
    @Column(name = "ticket_key", length = 50, nullable = false, updatable = false)
    private String ticketKey;

    /**
     * Ticket title when the ticket was added.
     */
    @Column(name = "ticket_title", length = 500, nullable = false, updatable = false)
    private String ticketTitle;

    /**
     * Board name when the ticket was added.
     */
    @Column(name = "board_name", length = 255, nullable = false, updatable = false)
    private String boardName;

    /**
     * Zero-based place of the ticket in the share's list; an edit moves a kept
     * ticket by changing it.
     */
    @Column(name = "position", nullable = false)
    private Integer position;

    // -------------Relations-----------//

    /**
     * Share this ticket row belongs to.
     *
     * No cascade -- the row is owned by {@link PullRequestShare#relatedTickets}.
     *
     * Fetch: EAGER -- Hibernate 6 does not support a LAZY to-one association to
     * an entity that uses @SoftDelete.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "share_id", nullable = false, updatable = false)
    private PullRequestShare share;
}
