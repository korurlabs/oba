package com.korur.oba.shrine.identity.enums;

/**
 * Who runs an agent, fixed when the agent is created.
 */
public enum AgentFlow {

    // The vendor hosts the agent; Oba fires the agent's routine and the session calls Oba with the
    // agent's API token.
    ROUTINE,

    // Oba runs the agent itself by calling a model, so the agent needs neither a routine nor a token.
    API
}
