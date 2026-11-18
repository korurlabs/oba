package com.korur.oba.apps.kaya.version.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.version.enums.FileChange;
import com.korur.oba.apps.kaya.version.enums.VersionSource;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;

/**
 * One version of a pack or project that added, changed or deleted the file at one path against its
 * base, as the version menu of a document lists it. The review domain adds the reviews of the
 * version to it.
 *
 * @param versionId   the version
 * @param number      the version number within its owner
 * @param status      the status of the version
 * @param source      where the version came from
 * @param change      how the version changed the path against its base
 * @param sha256      SHA-256 of the file in this version; null when it was deleted or is a link
 * @param current     whether the version is its owner's current version
 * @param proposedBy  the user who proposed the version
 * @param reason      the reason given on submit; null for a draft never submitted
 * @param submittedAt when the version was submitted; null for a draft never submitted
 * @param decidedAt   when a reviewer approved or rejected it; null before a decision
 */
public record FileHistoryEntry(
        UUID versionId,
        Integer number,
        VersionStatus status,
        VersionSource source,
        FileChange change,
        String sha256,
        Boolean current,
        UUID proposedBy,
        String reason,
        Instant submittedAt,
        Instant decidedAt
) {
}
