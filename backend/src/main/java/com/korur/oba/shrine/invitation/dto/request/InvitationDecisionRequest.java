package com.korur.oba.shrine.invitation.dto.request;

// 1. Framework-level
import jakarta.validation.constraints.NotNull;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.invitation.enums.InvitationDecision;

/**
 * Request to accept or decline an invitation.
 */
@Schema(description = "Invitation decision request")
public record InvitationDecisionRequest(
        @Schema(description = "ID of the invitation to act on", nullable = false)
        @NotNull(message = "Invitation ID is required")
        UUID invitationId,

        @Schema(description = "Decision to accept or decline the invitation", nullable = false)
        @NotNull(message = "Decision is required")
        InvitationDecision decision
) {
}
