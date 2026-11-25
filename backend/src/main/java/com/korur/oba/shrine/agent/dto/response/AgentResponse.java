package com.korur.oba.shrine.agent.dto.response;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserStatus;

/**
 * Response payload describing an agent of an organization, its live token and its routine. Neither the
 * token nor the routine secret is ever part of it; only their display prefixes are.
 */
@Schema(description = "Agent response")
public record AgentResponse(

        @Schema(description = "Agent id, the id of its user", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID id,

        @Schema(description = "Agent name", example = "Pentest agent")
        String name,

        @Schema(description = "Who runs the agent", example = "ROUTINE")
        AgentFlow flow,

        @Schema(description = "Vendor behind the agent", example = "ANTHROPIC")
        AgentProvider provider,

        @Schema(description = "Job the agent is registered for", example = "CYBERSECURITY_ENGINEER")
        AgentRole role,

        @Schema(description = "Apps granted to the agent", example = "[\"KAZAN\"]")
        List<App> apps,

        @Schema(description = "Status of the agent user, ACTIVE or DISABLED", example = "ACTIVE")
        UserStatus status,

        @Schema(description = "First 12 characters of the live token; null without one", nullable = true,
                example = "oat_Q2xhdWRl")
        String tokenPrefix,

        @Schema(description = "Last time the live token was used (UTC); null without one or before first use",
                nullable = true, example = "2026-11-24T10:15:30Z")
        Instant tokenLastUsedAt,

        @Schema(description = "When the live token stops working (UTC); null without one", nullable = true,
                example = "2027-11-24T10:15:30Z")
        Instant tokenExpiresAt,

        @Schema(description = "Id of the routine Oba fires; null without a routine", nullable = true,
                example = "trig_01AbCdEf")
        String routineId,

        @Schema(description = "First characters of the routine secret; null without a routine", nullable = true,
                example = "sk-ant-oat01")
        String routineSecretPrefix,

        @Schema(description = "Last time the routine was fired (UTC); null before the first fire", nullable = true,
                example = "2026-11-24T10:15:30Z")
        Instant lastFiredAt,

        @Schema(description = "Result of the last fire; null before the first fire", nullable = true, example = "FIRED")
        RoutineFireStatus lastFireStatus,

        @Schema(description = "Session the last successful fire started; null without one", nullable = true,
                example = "https://claude.ai/code/session_01AbCdEf")
        String lastSessionUrl
) {
}
