package com.korur.oba.apps.kaizen.ticket.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.systemactor.enums.SystemActorType;

/**
 * Response payload describing the system actor that created a ticket, such as
 * Alertmanager for an incident ticket. It has no user account, so clients show
 * it as a system creator instead of a person.
 */
@Schema(description = "Ticket created by system actor response")
public record TicketCreatedBySystemActorResponse(

        // System actor identifier.
        @Schema(description = "System actor id", nullable = false)
        UUID id,

        // Display name of the system actor.
        @Schema(description = "System actor name", nullable = false)
        String name,

        // Kind of system actor.
        @Schema(description = "System actor type", nullable = false)
        SystemActorType type
) {
}
