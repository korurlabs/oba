package com.korur.oba.apps.kaya.agentsfile.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;

/**
 * The index of one agents file as the render domain extracts it on parse.
 *
 * @param placement     where the file is delivered, see {@link AgentsFilePlacement}
 * @param contentBlobId the blob holding the whole file
 */
public record AgentsFileIndex(
        AgentsFilePlacement placement,
        UUID contentBlobId
) {
}
