package com.korur.oba.shrine.agent.constant.validator;

/**
 * Log messages of the agent refusals. They carry ids and the refused action only.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AgentValidatorLogConstant {

    private AgentValidatorLogConstant() {
    }

    public static final String AGENT_CALLER_REFUSED =
            "Agent caller refused: agentId={}, action={}";
    public static final String AGENT_TARGET_REFUSED =
            "Agent target refused: agentId={}, action={}";
}
