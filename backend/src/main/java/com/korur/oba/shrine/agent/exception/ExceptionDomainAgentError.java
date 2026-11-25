package com.korur.oba.shrine.agent.exception;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Errors of agents: their registration, their API token and what an agent may not do.
 */
public enum ExceptionDomainAgentError implements ExceptionDomainError {

    // ── Security (AGT_SEC) ───────────────────────────────────────────────────
    SEC_TOKEN_INVALID("AGT_SEC_001", "The agent token is missing, unknown, revoked or expired, or its agent is disabled.", ExceptionCategory.AUT),
    SEC_AGENT_ACTION_REFUSED("AGT_SEC_002", "An agent may not perform this action.", ExceptionCategory.SEC),

    // ── Not Found (AGT_NOT) ──────────────────────────────────────────────────
    NOT_AGENT_FOUND("AGT_NOT_001", "Agent not found.", ExceptionCategory.NOT),

    // ── State (AGT_STA) ──────────────────────────────────────────────────────
    STA_NAME_TAKEN("AGT_STA_001", "An agent with this name already exists in the organization.", ExceptionCategory.STA),

    // ── Validation (AGT_VAL) ─────────────────────────────────────────────────
    VAL_AGENT_TARGET_REFUSED("AGT_VAL_001", "This action does not apply to an agent.", ExceptionCategory.VAL),
    VAL_ROUTINE_ID_FORMAT("AGT_VAL_002", "The routine id does not have the format of the agent's provider.", ExceptionCategory.VAL),
    VAL_FLOW_PROVIDER_UNSUPPORTED("AGT_VAL_003", "The agent's flow does not support this provider or this action.", ExceptionCategory.VAL);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainAgentError(String code, String message, ExceptionCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ExceptionCategory getCategory() {
        return category;
    }
}
