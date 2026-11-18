package com.korur.oba.apps.kaya.settings.entity;

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
 * The .claude/settings.json of one {@link com.korur.oba.apps.kaya.version.entity.Version}; a version has at
 * most one.
 *
 * The blob named by contentBlobId is the exact JSON and is delivered byte-exact. There are no typed
 * columns: the permission rules and hooks are parsed from the blob by
 * {@link com.korur.oba.apps.kaya.settings.helper.SettingsParser} whenever they are shown, so they cannot
 * drift from the file.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version
 * domain and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.settings.repository.SettingsRepository} by
 * {@link com.korur.oba.apps.kaya.settings.service.SettingsQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.settings.service.SettingsService}, which the render domain calls on
 * parse; composed by the release domain.
 */
@Entity
@Table(name = "settings", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Settings extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;
}
