package com.korur.oba.apps.kaya.pack.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import com.korur.oba.apps.kaya.pack.constant.PackConstant;

/**
 * A named set of shared agent config of one organization, which repositories compose in an order
 * of their own.
 *
 * Its name is unique within the organization and matches {@link PackConstant#NAME_PATTERN}. The
 * organization and the current version are kept as plain ids, so this domain loads neither
 * identity nor version entities; currentVersionId is the newest approved version and null until the
 * first approval. A required pack is composed into every repository of the organization, ahead of
 * the repository's own pack order, so the limits it sets apply whatever a repository lists.

A pack may have a parent pack of the same organization, at any depth. Using a pack brings in its
whole chain of ancestors, and the pack stores only its own items and overrides. The parent is set
at creation and never changed, so no cycle can form.
 *
 * Read through {@link com.korur.oba.apps.kaya.pack.repository.PackRepository} by
 * {@link com.korur.oba.apps.kaya.pack.service.PackQueryService}; written by
 * {@link com.korur.oba.apps.kaya.pack.service.PackService}; versioned, released and seeded by the
 * version, release and seed domains.
 */
@Entity
@Table(name = "packs", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Pack extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // Updatable only for a rename, which PackService allows while no stored file, release, plan or
    // composition depends on the name yet.
    @Column(name = "name", length = PackConstant.NAME_MAX_LENGTH, nullable = false)
    private String name;

    // Plain text for the people choosing a pack; nothing composes or matches on it, so it is
    // editable at any time, also once the pack is in use.
    @Column(name = "description", length = PackConstant.DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "parent_pack_id", updatable = false)
    private UUID parentPackId;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Builder.Default
    @Column(name = "required", nullable = false)
    private Boolean required = false;
}
