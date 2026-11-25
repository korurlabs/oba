package com.korur.oba.shrine.agent.routine.constant.service;

/**
 * Log messages of the agent routine services. They carry ids only, never the routine secret.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AgentRoutineServiceLogConstant {

    private AgentRoutineServiceLogConstant() {
    }

    public static final String AGENT_ROUTINE_SET =
            "Agent routine set: organizationId={}, agentId={}, agentRoutineId={}, routineId={}, replaced={}";
    public static final String AGENT_ROUTINE_REMOVED =
            "Agent routine removed: organizationId={}, agentId={}, agentRoutineId={}";
    public static final String AGENT_NO_ROUTINE_TO_REMOVE =
            "Agent has no routine to remove: organizationId={}, agentId={}";
    public static final String AGENT_ROUTINE_FIRED =
            "Agent routine fired: organizationId={}, agentId={}, provider={}, routineId={}, status={}, httpStatus={}";
    public static final String AGENT_ROUTINE_FIRE_RECORDED =
            "Agent routine fire recorded: agentRoutineId={}, status={}";

    // Refusals and skips.
    public static final String AGENT_ROUTINE_FLOW_REFUSED =
            "Agent routine refused for the flow: organizationId={}, agentId={}, flow={}";
    public static final String AGENT_ROUTINE_ID_REFUSED =
            "Agent routine id refused for the provider format: organizationId={}, agentId={}, provider={}";
    public static final String AGENT_ROUTINE_FIRE_SKIPPED =
            "Agent routine fire skipped: organizationId={}, userId={}, reason={}";

    // Failures.
    public static final String AGENT_ROUTINE_FIRE_FAILED =
            "Agent routine fire failed: organizationId={}, agentId={}, provider={}, routineId={}, status={}, httpStatus={}";
    public static final String AGENT_ROUTINE_FIRE_ERROR =
            "Agent routine fire raised an error: organizationId={}, agentId={}, provider={}, routineId={}";
    public static final String AGENT_ROUTINE_RECORD_MISSING =
            "Agent routine vanished before its fire was recorded: agentRoutineId={}, status={}";

    // Skip reasons.
    public static final String SKIP_NOT_AGENT = "not an agent of the organization";
    public static final String SKIP_NOT_ACTIVE = "agent not active";
    public static final String SKIP_NOT_ROUTINE_FLOW = "agent not on the routine flow";
    public static final String SKIP_NO_ROUTINE_VENDOR = "provider has no routine vendor";
    public static final String SKIP_NO_ROUTINE = "agent has no routine";
}
