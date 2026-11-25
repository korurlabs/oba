package com.korur.oba.apps.kazan.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserKind;

/**
 * Response payload describing a ticket assignee.
 */
@Schema(description = "Ticket assignee response")
public record TicketAssigneeResponse(

        // Unique assignee record identifier.
        @Schema(description = "Assignee id", nullable = false)
        UUID id,

        // User identifier for the assignee.
        @Schema(description = "User id", nullable = false)
        UUID userId,

        // Assignee email address.
        @Schema(description = "User email", nullable = false)
        String email,

        // Assignee first name.
        @Schema(description = "User first name", nullable = false)
        String firstName,

        // Assignee last name.
        @Schema(description = "User last name", nullable = false)
        String lastName,

        // Whether the assignee is a person or an agent, so a ticket can badge an agent assignee.
        @Schema(description = "User kind", nullable = false, example = "HUMAN")
        UserKind kind,

        // The job an agent is registered for; null for a person.
        @Schema(description = "Agent role; null for a person", nullable = true)
        AgentRole agentRole,

        // Who runs an agent; null for a person.
        @Schema(description = "Agent flow; null for a person", nullable = true)
        AgentFlow agentFlow,

        // The vendor behind an agent; null for a person.
        @Schema(description = "Agent provider; null for a person", nullable = true)
        AgentProvider agentProvider
) {
}
