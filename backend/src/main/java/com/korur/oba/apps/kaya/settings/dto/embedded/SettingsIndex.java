package com.korur.oba.apps.kaya.settings.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The index of a version's .claude/settings.json as the render domain extracts it on parse. Settings
 * have no typed columns: the permission rules and hooks are read from the blob when they are shown.
 *
 * @param contentBlobId the blob holding the exact JSON
 */
public record SettingsIndex(
        UUID contentBlobId
) {
}
