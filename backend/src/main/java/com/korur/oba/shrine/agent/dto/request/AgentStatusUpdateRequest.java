package com.korur.oba.shrine.agent.dto.request;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

/**
 * Request payload to enable or disable an agent: its user and its membership take the status.
 */
@Schema(description = "Agent status update request")
public record AgentStatusUpdateRequest(

        @Schema(description = "New status", requiredMode = Schema.RequiredMode.REQUIRED, example = "DISABLED")
        @NotNull(message = "Status is required")
        OrganizationMemberStatus status
) {
}
