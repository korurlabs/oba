package com.korur.oba.apps.kalkan.page.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * The text a write gave one translation of a page, kept so that the content history of a
 * documented-information page can always be shown: the rows of a page and locale, by page version,
 * are its full history.
 *
 * Append-only: no code updates or deletes a revision, so it has no deleted flag. The page is a
 * plain id rather than a relation, so a revision of a deleted page still loads.
 */
@Entity
@Table(name = "page_revisions", schema = "kalkan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class KalkanPageRevision extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "page_id", nullable = false, updatable = false)
    private UUID pageId;

    /**
     * The page version the write produced.
     */
    @Column(name = "page_version", nullable = false, updatable = false)
    private long pageVersion;

    @Column(name = "title", length = 200, nullable = false, updatable = false)
    private String title;

    @Column(name = "body", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private UUID createdById;

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "locale", nullable = false, updatable = false)
    private KalkanLocale locale;
}
