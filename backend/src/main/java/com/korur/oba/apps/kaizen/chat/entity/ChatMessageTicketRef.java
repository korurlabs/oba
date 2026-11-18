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
 * Entity representing one ticket referenced by a chat message.
 *
 * The reference is a snapshot, as a daily update ticket line: key, title and
 * board ID are copied when the message is written or edited, so the message
 * reads the same after a rename and survives the ticket's deletion. The
 * ticket and board are therefore plain ID columns; the database clears
 * ticket_id when the ticket row is removed.
 */
@Entity
@Table(name = "chat_message_ticket_refs", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageTicketRef extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Ticket the message references; null once the ticket row is removed.
     */
    @Column(name = "ticket_id", nullable = true)
    private UUID ticketId;

    /**
     * Board the ticket was on when the message was written.
     */
    @Column(name = "board_id", nullable = false)
    private UUID boardId;

    @Column(name = "ticket_key", length = 50, nullable = false)
    private String ticketKey;

    @Column(name = "ticket_title", length = 500, nullable = false)
    private String ticketTitle;

    /**
     * Zero-based place of the reference among the message's references.
     */
    @Column(name = "position", nullable = false)
    private Integer position;

    // -------------Relations-----------//

    /**
     * Message that references the ticket.
     *
     * No cascade -- the reference is owned by {@link ChatMessage#ticketRefs}.
     *
     * Fetch: EAGER -- Hibernate 6 does not support a LAZY to-one association to
     * an entity that uses @SoftDelete.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;
}
