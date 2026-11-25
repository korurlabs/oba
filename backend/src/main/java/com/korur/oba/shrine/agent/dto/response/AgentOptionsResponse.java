package com.korur.oba.shrine.agent.dto.response;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

/**
 * The flow and provider combinations an agent can be registered with, read from the connectors of the
 * running application, so a client never hard-codes them.
 */
@Schema(description = "Agent options response")
public record AgentOptionsResponse(

        @Schema(description = "Every flow with the providers it accepts")
        List<AgentFlowOptionResponse> flows
) {
}
