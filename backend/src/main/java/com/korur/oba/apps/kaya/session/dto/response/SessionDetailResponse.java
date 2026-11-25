package com.korur.oba.apps.kaya.session.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.session.enums.SessionDeliveredBy;
import com.korur.oba.apps.kaya.session.enums.SessionReleaseState;

@Schema(description = "A session with what its view shows: subagents, skills used, handoffs and links")
public record SessionDetailResponse(
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

        @Schema(description = "Display label of the machine", example = "ada-macbook")
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

        @Schema(description = "The /rename title, when the session has one", nullable = true)
        String sessionTitle,

        @Schema(description = "First 500 characters of the first prompt", nullable = true)
        String firstPrompt,

        @Schema(description = "Working directory of the session", nullable = true)
        String cwd,

        @Schema(description = "git config user.email of the checkout", nullable = true)
        String gitAuthorEmail,

        @Schema(description = "Subagents, by start")
        List<SessionSubagentResponse> subagents,

        @Schema(description = "Skills used, by first use")
        List<SessionSkillUseResponse> skillUses,

        @Schema(description = "Handoff files, by path; GET /sessions/{sessionId}/handoffs returns their content")
        List<SessionHandoffSummaryResponse> handoffs,

        @Schema(description = "The repository release the session ran under; null when none was reported or matched",
                nullable = true, example = "3e4f5a6b-7c8d-4e9f-0a1b-2c3d4e5f6a7b")
        UUID releaseId,

        @Schema(description = "Number of that release; GET /repositories/{repositoryId}/releases/{number} shows its manifest",
                nullable = true, example = "7")
        Integer releaseNumber,

        @Schema(description = "FRESH, CACHED, STALE, NONE or UNVERIFIED; null when the recorder reported no release",
                nullable = true, example = "FRESH")
        SessionReleaseState releaseState,

        @Schema(description = "LAUNCHER or HOOK; null when not reported", nullable = true, example = "LAUNCHER")
        SessionDeliveredBy deliveredBy,

        @Schema(description = "When the session row was created")
        Instant createdAt,

        @Schema(description = "When the session last changed")
        Instant updatedAt
) {
}
