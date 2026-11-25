package com.korur.oba.shrine.agent.constant;

/**
 * Field names, audit keys and fixed values of agents.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AgentConstant {

    private AgentConstant() {
    }

    // An agent user is stored under an address no person can own: the .invalid top-level domain is
    // reserved and never resolves, so no invitation or mail can reach it.
    public static final String AGENT_EMAIL_PREFIX = "agent-";
    public static final String AGENT_EMAIL_DOMAIN = "@agents.oba.invalid";

    // The unique index that keeps one live agent name per organization, ignoring case.
    public static final String AGENT_NAME_UNIQUE_INDEX = "users_agent_organization_id_first_name_uk";

    // Error value and audit field names.
    public static final String FIELD_AGENT_ID = "agentId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_FLOW = "flow";
    public static final String FIELD_PROVIDER = "provider";
    public static final String FIELD_ROLE = "role";
    public static final String FIELD_APPS = "apps";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_TOKEN_ID = "tokenId";
    public static final String FIELD_TOKEN_PREFIX = "tokenPrefix";
    public static final String FIELD_EXPIRES_AT = "expiresAt";
    public static final String FIELD_REVOKED_TOKEN_ID = "revokedTokenId";
    public static final String FIELD_SOFT_DELETED = "softDeleted";

    // Audit entity type and actions.
    public static final String AUDIT_ENTITY_TYPE = "Agent";
    public static final String AUDIT_ACTION_AGENT_CREATED = "AGENT_CREATED";
    public static final String AUDIT_ACTION_AGENT_UPDATED = "AGENT_UPDATED";
    public static final String AUDIT_ACTION_AGENT_STATUS_CHANGED = "AGENT_STATUS_CHANGED";
    public static final String AUDIT_ACTION_AGENT_DELETED = "AGENT_DELETED";
    public static final String AUDIT_ACTION_AGENT_TOKEN_ISSUED = "AGENT_TOKEN_ISSUED";
    public static final String AUDIT_ACTION_AGENT_TOKEN_REVOKED = "AGENT_TOKEN_REVOKED";
}
