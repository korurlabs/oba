package com.korur.oba.apps.kaya.repository.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;

/**
 * A git repository an organization records sessions for.
 *
 * The organization is kept as its id rather than a relation, so the repository domain does not
 * load identity entities; every query filters by it. The remote URL is stored normalised as
 * host/owner/name in lower case, so a recorder's git remote in any form (HTTPS, SSH, with or
 * without .git) matches the same row. An ARCHIVED repository refuses ingest.
 *
 * Read through {@link com.korur.oba.apps.kaya.repository.repository.RepositoryRepository} by
 * {@link com.korur.oba.apps.kaya.repository.service.RepositoryQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.repository.service.RepositoryService}; read by the release, project,
 * delivery, plan and session domains, for example
 * {@link com.korur.oba.apps.kaya.release.service.ReleaseService} and
 * {@link com.korur.oba.apps.kaya.session.service.SessionQueryService}.
 */
@Entity
@Table(name = "repositories", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Repository extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "remote_url", length = 500, nullable = false, updatable = false)
    private String remoteUrl;

    // Contains {key}, which the session view replaces with a ticket key to build the ticket link.
    @Column(name = "ticket_url_template", length = 500)
    private String ticketUrlTemplate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private RepositoryStatus status = RepositoryStatus.ACTIVE;
}
