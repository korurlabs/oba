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
    public static final String FIELD_RETIRED_MACHINES = "retiredMachines";
    public static final String FIELD_SOFT_DELETED = "softDeleted";
    public static final String FIELD_ACTION = "action";
    public static final String FIELD_USER_ID = "userId";

    // Actions an agent may not take as the caller (AGT_SEC_002). An agent never closes, deletes or
    // outlives its work and never mints a credential, so a prompt-injected run cannot widen its reach.
    public static final String ACTION_TICKET_DONE = "TICKET_DONE";
    public static final String ACTION_TICKET_DELETE = "TICKET_DELETE";
    // Nor does an agent rewrite a ticket's title or description: a person approves a plan against
    // that text, and the build run must work from what was approved.
    public static final String ACTION_TICKET_TEXT_EDIT = "TICKET_TEXT_EDIT";
    public static final String ACTION_ORGANIZATION_LEAVE = "ORGANIZATION_LEAVE";
    public static final String ACTION_KAYA_MACHINE_CREATE = "KAYA_MACHINE_CREATE";
    public static final String ACTION_KAYA_TOKEN_ROTATE = "KAYA_TOKEN_ROTATE";
    // Nor does an agent change what Kaya delivers into people's sessions, erase the record of its own
    // runs or cut off the machine token its runs record with.
    public static final String ACTION_KAYA_ACCESS = "KAYA_ACCESS";
    public static final String ACTION_KAYA_LESSON_CREATE = "KAYA_LESSON_CREATE";
    public static final String ACTION_KAYA_LESSON_UPDATE = "KAYA_LESSON_UPDATE";
    public static final String ACTION_KAYA_LESSON_DELETE = "KAYA_LESSON_DELETE";
    public static final String ACTION_KAYA_PLAN_FEEDBACK = "KAYA_PLAN_FEEDBACK";
    public static final String ACTION_KAYA_SESSION_DELETE = "KAYA_SESSION_DELETE";
    public static final String ACTION_KAYA_TOKEN_REVOKE = "KAYA_TOKEN_REVOKE";
    public static final String ACTION_KAYA_MACHINE_RETIRE = "KAYA_MACHINE_RETIRE";
    // Nor does an agent propose configuration: an approved version releases hooks and settings that
    // run on every machine of the organization, so only a person drafts and proposes one.
    public static final String ACTION_KAYA_CONFIG_DRAFT = "KAYA_CONFIG_DRAFT";
    public static final String ACTION_KAYA_CONFIG_PROPOSE = "KAYA_CONFIG_PROPOSE";
    // Nor does an agent assign a ticket to another agent: the assignment starts that agent's run, and
    // only a person starts an agent.
    public static final String ACTION_TICKET_AGENT_ASSIGNEE = "TICKET_AGENT_ASSIGNEE";

    // Actions that do not apply to an agent as the target (AGT_VAL_001): its membership and status are
    // managed on the Agents page, and its role on a board stays MEMBER.
    public static final String ACTION_MEMBER_REMOVE = "MEMBER_REMOVE";
    public static final String ACTION_MEMBER_RESTORE = "MEMBER_RESTORE";
    public static final String ACTION_MEMBER_ROLE = "MEMBER_ROLE";
    public static final String ACTION_MEMBER_STATUS = "MEMBER_STATUS";
    public static final String ACTION_TICKET_REVIEWER = "TICKET_REVIEWER";
    public static final String ACTION_BOARD_ROLE = "BOARD_ROLE";

    // Audit entity type and actions.
    public static final String AUDIT_ENTITY_TYPE = "Agent";
    public static final String AUDIT_ACTION_AGENT_CREATED = "AGENT_CREATED";
    public static final String AUDIT_ACTION_AGENT_UPDATED = "AGENT_UPDATED";
    public static final String AUDIT_ACTION_AGENT_STATUS_CHANGED = "AGENT_STATUS_CHANGED";
    public static final String AUDIT_ACTION_AGENT_DELETED = "AGENT_DELETED";
    public static final String AUDIT_ACTION_AGENT_TOKEN_ISSUED = "AGENT_TOKEN_ISSUED";
    public static final String AUDIT_ACTION_AGENT_TOKEN_REVOKED = "AGENT_TOKEN_REVOKED";
}
