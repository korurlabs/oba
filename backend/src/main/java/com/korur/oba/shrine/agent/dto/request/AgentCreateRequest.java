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
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;

/**
 * Request payload to register an agent in an organization. Flow and provider are fixed once the agent
 * exists; the apps become one USER grant each.
 */
@Schema(description = "Agent create request")
public record AgentCreateRequest(

        @Schema(description = "Agent name, unique in the organization ignoring case",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Pentest agent")
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Schema(description = "Who runs the agent", requiredMode = Schema.RequiredMode.REQUIRED, example = "ROUTINE")
        @NotNull(message = "Flow is required")
        AgentFlow flow,

        @Schema(description = "Vendor behind the agent; ROUTINE accepts only a provider with a routine connector",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "ANTHROPIC")
        @NotNull(message = "Provider is required")
        AgentProvider provider,

        @Schema(description = "Job the agent is registered for", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "CYBERSECURITY_ENGINEER")
        @NotNull(message = "Role is required")
        AgentRole role,

        @Schema(description = "Apps the agent may use", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "[\"KAZAN\"]")
        @NotEmpty(message = "At least one app is required")
        Set<@NotNull App> apps
) {
}
