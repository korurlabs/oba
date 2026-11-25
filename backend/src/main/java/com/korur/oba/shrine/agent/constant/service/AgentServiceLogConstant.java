package com.korur.oba.shrine.agent.constant.service;

/**
 * Log messages of the agent services. They carry ids only, never a token or a secret.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AgentServiceLogConstant {

    private AgentServiceLogConstant() {
    }

    public static final String AGENT_CREATED =
            "Agent created: organizationId={}, agentId={}, flow={}, provider={}, apps={}";
    public static final String AGENT_UPDATED =
            "Agent updated: organizationId={}, agentId={}, apps={}";
    public static final String AGENT_STATUS_CHANGED =
            "Agent status changed: organizationId={}, agentId={}, status={}";
    public static final String AGENT_STATUS_UNCHANGED =
            "Agent status already set: organizationId={}, agentId={}, status={}";
    public static final String AGENT_DELETED =
            "Agent deleted: organizationId={}, agentId={}, revokedTokenId={}";
    public static final String AGENT_TOKEN_ISSUED =
            "Agent token issued: organizationId={}, agentId={}, tokenId={}, revokedTokenId={}";
    public static final String AGENT_TOKEN_REVOKED =
            "Agent token revoked: organizationId={}, agentId={}, tokenId={}";
    public static final String AGENT_NO_TOKEN_TO_REVOKE =
            "Agent has no live token to revoke: organizationId={}, agentId={}";

    // Refusals.
    public static final String AGENT_NOT_FOUND =
            "Agent not found: organizationId={}, agentId={}";
    public static final String AGENT_NAME_TAKEN =
            "Agent name taken: organizationId={}, agentId={}";
    public static final String AGENT_FLOW_PROVIDER_REFUSED =
            "Agent flow and provider combination refused: organizationId={}, flow={}, provider={}";
    public static final String AGENT_TOKEN_FLOW_REFUSED =
            "Agent token refused for the flow: organizationId={}, agentId={}, flow={}";
    public static final String AGENT_MEMBERSHIP_MISSING =
            "Agent membership missing: organizationId={}, agentId={}";
}
