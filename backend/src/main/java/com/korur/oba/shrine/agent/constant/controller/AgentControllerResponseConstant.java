package com.korur.oba.shrine.agent.constant.controller;

/**
 * Response messages of the agent endpoints.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AgentControllerResponseConstant {

    private AgentControllerResponseConstant() {
    }

    public static final String RESPONSE_AGENTS_RETRIEVED = "Agents retrieved successfully";
    public static final String RESPONSE_AGENT_OPTIONS_RETRIEVED = "Agent options retrieved successfully";
    public static final String RESPONSE_AGENT_CREATED = "Agent created successfully";
    public static final String RESPONSE_AGENT_UPDATED = "Agent updated successfully";
    public static final String RESPONSE_AGENT_STATUS_UPDATED = "Agent status updated successfully";
    public static final String RESPONSE_AGENT_DELETED = "Agent deleted successfully";
    public static final String RESPONSE_AGENT_TOKEN_ISSUED = "Agent token issued successfully";
    public static final String RESPONSE_AGENT_TOKEN_REVOKED = "Agent token revoked successfully";
    public static final String RESPONSE_AGENT_ROUTINE_SET = "Agent routine set successfully";
    public static final String RESPONSE_AGENT_ROUTINE_REMOVED = "Agent routine removed successfully";
}
