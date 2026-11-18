package com.korur.oba.apps.kalkan.permission.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * A grant of a level on a root page to one member.
 *
 * A grant is never changed in place: a new level is a new row and the old row is soft-deleted, so
 * a deleted row's created_at to updated_at is the period of that grant, createdById the granter
 * and updatedById the revoker. At most one live row exists per root and member.
 */
@Entity
@Table(name = "page_permissions", schema = "kalkan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class KalkanPagePermission extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private UUID createdById;

    @Column(name = "updated_by_id", nullable = false)
    private UUID updatedById;

    // -------------Relations-----------//

    /**
     * The root page the level is granted on.
     *
     * No cascade: the grants of a deleted root stay live, so a restore brings back the access as
     * it was.
     *
     * Fetch: EAGER, required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "page_id", nullable = false, updatable = false)
    private KalkanPage page;

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "level", nullable = false, updatable = false)
    private KalkanLevel level;
}
