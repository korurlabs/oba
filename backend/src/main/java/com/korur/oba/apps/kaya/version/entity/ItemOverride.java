package com.korur.oba.apps.kaya.version.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * The record that an item of a version replaces an item an ancestor pack delivers, with the
 * ancestor and its version the replacement was last reviewed against. It belongs to the version
 * like its files, so a draft carries it and a change of the base is reviewed like a change of
 * content. The item is a file path, or a skill directory ending in a slash.
 */
@Entity
@Table(name = "item_overrides", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ItemOverride extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "path", nullable = false, updatable = false, length = 500)
    private String path;

    @Column(name = "base_pack_id", nullable = false)
    private UUID basePackId;

    // Null when no reviewed base is known; the database sets it null when that version goes.
    @Column(name = "base_version_id")
    private UUID baseVersionId;
}
