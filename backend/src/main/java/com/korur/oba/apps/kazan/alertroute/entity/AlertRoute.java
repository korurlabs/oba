package com.korur.oba.apps.kazan.alertroute.entity;

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
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity mapping the system an Alertmanager alert comes from to the board that
 * receives its incident tickets.
 *
 * Each organization keeps its own map: one row per system in an organization,
 * enforced by a unique constraint; several systems may share a board. At most one
 * row per organization is the default, which receives the alerts of systems
 * without a row; a partial unique index enforces it. A removed mapping has no
 * history worth keeping, so rows are deleted outright, without soft delete.
 */
@Entity
@Table(name = "alert_routes", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AlertRoute extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Value of the alert's system label. Fixed after create: changing it would
     * silently move another system's alerts, so a new system gets a new row.
     */
    @Column(name = "system_key", length = 50, nullable = false, updatable = false)
    private String systemKey;

    /**
     * Organization that owns the route, set once on create from the request's
     * checked organization and never from a body. A route can only point to a
     * board of the same organization, which the database enforces.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    // -------------Relations-----------//

    /**
     * Board that receives the system's incidents.
     *
     * No cascade -- the mapping does not own the board. A purged board removes
     * its mappings through the foreign key's ON DELETE CASCADE.
     *
     * Fetch: EAGER -- the target board uses
     * {@link org.hibernate.annotations.SoftDelete}, which Hibernate 6 does not
     * support with LAZY on this side.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;
}
