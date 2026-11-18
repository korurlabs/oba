package com.korur.oba.apps.kaya.version.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileMode;

/**
 * The state of one path as the release a CLI proposal was made against delivered it. Two states are
 * equal exactly when content hash, mode and link target are equal, so a path whose current state no
 * longer matches has moved on since that release.
 *
 * @param sha256        SHA-256 of the content; null for a link
 * @param mode          the file mode
 * @param symlinkTarget the target of a link; null otherwise
 */
public record DeliveredFile(String sha256, FileMode mode, String symlinkTarget) {
}
