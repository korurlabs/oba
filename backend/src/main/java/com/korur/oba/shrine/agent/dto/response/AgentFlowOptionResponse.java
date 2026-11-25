package com.korur.oba.shrine.agent.dto.response;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;

/**
 * One flow an agent can be registered with and the providers it accepts.
 */
@Schema(description = "Agent flow option")
public record AgentFlowOptionResponse(

        @Schema(description = "Flow", example = "ROUTINE")
        AgentFlow flow,

        @Schema(description = "Providers the flow accepts", example = "[\"ANTHROPIC\"]")
        List<AgentProvider> providers
) {
}
