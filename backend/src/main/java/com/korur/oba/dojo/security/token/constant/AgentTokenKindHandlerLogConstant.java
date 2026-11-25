package com.korur.oba.dojo.security.token.constant;

/**
 * Log messages of the agent token kind. They carry ids only, never the token.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AgentTokenKindHandlerLogConstant {

    private AgentTokenKindHandlerLogConstant() {
    }

    public static final String AGENT_TOKEN_REFUSED =
            "Agent token refused: tokenId={}, agentId={}, organizationId={}, reason={}";

    // Reasons of a refusal.
    public static final String REASON_USER_MISSING = "user missing";
    public static final String REASON_NOT_AGENT = "user is not an agent of the organization";
    public static final String REASON_USER_NOT_ACTIVE = "agent user not active";
    public static final String REASON_MEMBERSHIP_NOT_ACTIVE = "membership missing or not active";
    public static final String REASON_ORGANIZATION_NOT_ACTIVE = "organization not active";
}
