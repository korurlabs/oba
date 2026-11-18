package com.korur.oba.apps.kaya.session.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal

@Schema(description = "A session on the wall")
public record SessionListResponse(
        @Schema(description = "Session id")
        UUID id,

        @Schema(description = "Claude Code session id; the session's key in every session path")
        String sessionId,

        @Schema(description = "The /rename title, else the first prompt; empty while the session has neither", example = "Add the wall")
        String title,

        @Schema(description = "Repository id")
        UUID repositoryId,

        @Schema(description = "Repository name", example = "village")
        String repositoryName,

        @Schema(description = "User id")
        UUID userId,

        @Schema(description = "User display name", example = "Ada Lovelace")
        String userDisplayName,

        @Schema(description = "Machine id")
        UUID machineId,

        @Schema(description = "Hostname of the machine", example = "ada-macbook")
        String machineName,

        @Schema(description = "Another user holding a git email the session reported; the wall shows \"Identity of <name>\"",
                nullable = true)
        UUID identityConflictUserId,

        @Schema(description = "Display name of identityConflictUserId", nullable = true)
        String identityConflictUserDisplayName,

        @Schema(description = "Branch checked out", nullable = true, example = "feat/kaya")
        String branch,

        @Schema(description = "Model of the session; null when not reported", nullable = true, example = "claude-opus-5-5")
        String model,

        @Schema(description = "Claude Code version; null when not reported", nullable = true, example = "2.3.1")
        String claudeCodeVersion,

        @Schema(description = "Claude account email the session ran on, copied when it started", nullable = true)
        String accountEmail,

        @Schema(description = "Claude organization UUID (subscription)", nullable = true)
        String accountOrganizationUuid,

        @Schema(description = "Claude organization name", nullable = true)
        String accountOrganizationName,

        @Schema(description = "Billing type of the account", nullable = true)
        String billingType,

        @Schema(description = "Seat tier of the account", nullable = true)
        String seatTier,

        @Schema(description = "First event")
        Instant startedAt,

        @Schema(description = "Last event; the wall is ordered by it, newest first")
        Instant lastEventAt,

        @Schema(description = "true once the session ended")
        boolean ended,

        @Schema(description = "Events stored", example = "128")
        int eventCount,

        @Schema(description = "Handoff directories the session wrote")
        List<String> features,

        @Schema(description = "Pull requests and Kazan tickets, PULL_REQUEST first, then by reference")
        List<SessionLinkResponse> links,

        @Schema(description = "When the session row was created")
        Instant createdAt
) {
}
