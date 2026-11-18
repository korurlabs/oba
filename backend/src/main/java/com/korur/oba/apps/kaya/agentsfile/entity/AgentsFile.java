package com.korur.oba.apps.kaya.agentsfile.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
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
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;

/**
 * An AGENTS.md file of one {@link com.korur.oba.apps.kaya.version.entity.Version}, delivered at the place
 * its {@link AgentsFilePlacement} names.
 *
 * The whole file is the blob named by contentBlobId and is delivered byte-exact. The placement takes the
 * place of a path: an agents file is only ever delivered at a few fixed places, so a free path would
 * admit values no session loads, while the placement names exactly the allowed ones and lets the
 * database hold a version to at most one agents file per placement.
 * {@link com.korur.oba.apps.kaya.render.helper.DeliveryPaths} maps between a path and its placement.
 *
 * A version stores its agents files at the placements its own files name, the repository root or
 * .agents/. {@link AgentsFilePlacement#MOUNT_ROOT} is not stored: when a release composes a pack mounted
 * in a folder, {@link com.korur.oba.apps.kaya.release.helper.Composer} moves the pack's root AGENTS.md into
 * that folder, so a session working there loads it. The tool links, such as CLAUDE.md, point at these
 * files through {@link com.korur.oba.apps.kaya.render.helper.ToolPaths}.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version
 * domain and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.agentsfile.repository.AgentsFileRepository} by
 * {@link com.korur.oba.apps.kaya.agentsfile.service.AgentsFileQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.agentsfile.service.AgentsFileService}, which the render domain calls on
 * parse; composed by the release domain.
 */
@Entity
@Table(name = "agents_files", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AgentsFile extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "placement", nullable = false)
    private AgentsFilePlacement placement;
}
