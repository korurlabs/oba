package com.korur.oba.apps.kalkan.page.entity;

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
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;

/**
 * The title and Markdown body of a page in one locale.
 *
 * A translation is never removed alone, only with its page: a page delete soft-deletes its
 * translations at the same instant, so a restore brings back exactly those. At most one live
 * translation exists per page and locale.
 */
@Entity
@Table(name = "page_translations", schema = "kalkan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class KalkanPageTranslation extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private UUID createdById;

    @Column(name = "updated_by_id", nullable = false)
    private UUID updatedById;

    // -------------Relations-----------//

    /**
     * The page this text belongs to.
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
    @Column(name = "locale", nullable = false, updatable = false)
    private KalkanLocale locale;
}
