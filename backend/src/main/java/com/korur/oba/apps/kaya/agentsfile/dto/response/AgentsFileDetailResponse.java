package com.korur.oba.apps.kaya.agentsfile.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;

/**
 * One agents file with its placement and the whole file as stored.
 */
@Schema(description = "An agents file of a version in full, with the file as stored")
public record AgentsFileDetailResponse(
        @Schema(description = "Agents file id", example = "8f2e3d4c-5b6a-4f7e-8d9c-2b3c4d5e6f7a")
        UUID id,

        @Schema(description = "Version the agents file belongs to", example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId,

        @Schema(description = "Where the file is delivered: ROOT is AGENTS.md, AGENTS_DIR is .agents/AGENTS.md, "
                + "MOUNT_ROOT is AGENTS.md in the folder a pack is mounted in", example = "ROOT")
        AgentsFilePlacement placement,

        @Schema(description = "Byte length of the file", example = "4096")
        Integer sizeBytes,

        @Schema(description = "The whole file as UTF-8 text", example = "# Agent instructions\n")
        String content,

        @Schema(description = "When the agents file was stored", example = "2026-10-23T09:15:00Z")
        Instant createdAt,

        @Schema(description = "When the agents file was last changed", example = "2026-10-23T09:15:00Z")
        Instant updatedAt
) {
}
