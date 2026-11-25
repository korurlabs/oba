package com.korur.oba.shrine.agent.routine.constant;

/**
 * Field names, audit actions and fixed values of agent routines.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AgentRoutineConstant {

    private AgentRoutineConstant() {
    }

    // The visible start of a routine secret. Never more than half of the secret, so a short secret is
    // not shown whole.
    public static final int SECRET_PREFIX_LENGTH = 12;

    // Error value and audit field names.
    public static final String FIELD_ROUTINE_ID = "routineId";
    public static final String FIELD_AGENT_ROUTINE_ID = "agentRoutineId";

    // Audit actions; the entity type is the agent's.
    public static final String AUDIT_ACTION_AGENT_ROUTINE_SET = "AGENT_ROUTINE_SET";
    public static final String AUDIT_ACTION_AGENT_ROUTINE_REMOVED = "AGENT_ROUTINE_REMOVED";
}
