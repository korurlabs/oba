package com.korur.oba.apps.kaya.settings.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.settings.dto.embedded.SettingsIndex;
import com.korur.oba.apps.kaya.settings.entity.Settings;

/**
 * Writes the {@link Settings} of a version from the index the parse extracted. It parses nothing and
 * calls no other domain; the blob it references is stored by the caller.
 *
 * Called only by the render domain, inside the transaction that stores a version's files, so the row
 * commits together with the bytes it describes.
 */
public interface SettingsService {

    /**
     * Replaces the settings of a version: removes the existing row and writes the given one.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param settings       the index of the version's settings file, or null when the version has none
     * @return the settings written; empty when settings was null
     */
    Optional<Settings> replaceVersion(UUID organizationId, UUID versionId, SettingsIndex settings);
}
