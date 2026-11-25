package com.korur.oba.shrine.agent.dto.request;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.Set;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.identity.enums.AgentRole;

/**
 * Request payload to change an agent. Flow and provider are left out: another flow or provider is
 * another agent. The apps replace the agent's app grants.
 */
@Schema(description = "Agent update request")
public record AgentUpdateRequest(

        @Schema(description = "Agent name, unique in the organization ignoring case",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Pentest agent")
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Schema(description = "Job the agent is registered for", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "SALES_ENGINEER")
        @NotNull(message = "Role is required")
        AgentRole role,

        @Schema(description = "Apps the agent may use", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "[\"KAZAN\"]")
        @NotEmpty(message = "At least one app is required")
        Set<@NotNull App> apps
) {
}
