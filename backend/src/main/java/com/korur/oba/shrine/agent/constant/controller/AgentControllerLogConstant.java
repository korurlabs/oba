package com.korur.oba.shrine.agent.constant.controller;

/**
 * Log messages of the agent controller.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AgentControllerLogConstant {

    private AgentControllerLogConstant() {
    }

    public static final String AGENTS_LIST_REQUEST = "Agents list request: organizationId={}";
    public static final String AGENTS_LIST_REQUEST_COMPLETED = "Agents list request completed: organizationId={}, count={}";
    public static final String AGENT_OPTIONS_REQUEST = "Agent options request: organizationId={}";
    public static final String AGENT_OPTIONS_REQUEST_COMPLETED = "Agent options request completed: organizationId={}";
    public static final String AGENT_CREATE_REQUEST = "Agent create request: organizationId={}, flow={}, provider={}";
    public static final String AGENT_CREATE_REQUEST_COMPLETED = "Agent create request completed: organizationId={}, agentId={}";
    public static final String AGENT_UPDATE_REQUEST = "Agent update request: organizationId={}, agentId={}";
    public static final String AGENT_UPDATE_REQUEST_COMPLETED = "Agent update request completed: organizationId={}, agentId={}";
    public static final String AGENT_STATUS_REQUEST = "Agent status request: organizationId={}, agentId={}, status={}";
    public static final String AGENT_STATUS_REQUEST_COMPLETED = "Agent status request completed: organizationId={}, agentId={}";
    public static final String AGENT_DELETE_REQUEST = "Agent delete request: organizationId={}, agentId={}";
    public static final String AGENT_DELETE_REQUEST_COMPLETED = "Agent delete request completed: organizationId={}, agentId={}";
    public static final String AGENT_TOKEN_ISSUE_REQUEST = "Agent token issue request: organizationId={}, agentId={}";
    public static final String AGENT_TOKEN_ISSUE_REQUEST_COMPLETED = "Agent token issue request completed: organizationId={}, agentId={}";
    public static final String AGENT_TOKEN_REVOKE_REQUEST = "Agent token revoke request: organizationId={}, agentId={}";
    public static final String AGENT_TOKEN_REVOKE_REQUEST_COMPLETED = "Agent token revoke request completed: organizationId={}, agentId={}";
}
