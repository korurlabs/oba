package com.korur.oba.apps.kaizen.comment.entity;

// 1. Framework-level
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Entity representing a comment on a ticket.
 *
 * Comments allow users to discuss and provide updates on tickets.
 */
@Entity
@Table(name = "comments", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class Comment extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    // -------------Relations-----------//

    /**
     * Ticket this comment belongs to.
     *
     * No cascade -- ticket lifecycle is independent. Comment is owned by the
     * ticket via the Ticket.comments collection.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /**
     * User who authored this comment.
     *
     * No cascade -- user lifecycle is independent of the comment.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;
}
