package com.korur.oba.shrine.agent.dto.response;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload of a token issued to an agent. The plaintext token is returned this once; Oba keeps
 * only its hash.
 */
@Schema(description = "Agent token created response")
public record AgentTokenCreatedResponse(

        @Schema(description = "Agent id", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID agentId,

        @Schema(description = "The agent token in plaintext, shown once",
                example = "oat_Q2xhdWRlQ29kZVJvdXRpbmVUb2tlbkV4YW1wbGUwMDA")
        String token,

        @Schema(description = "First 12 characters of the token", example = "oat_Q2xhdWRl")
        String tokenPrefix,

        @Schema(description = "When the token stops working (UTC)", example = "2027-11-24T10:15:30Z")
        Instant expiresAt
) {
}
