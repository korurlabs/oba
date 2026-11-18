package com.korur.oba.shrine.access.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for app access (APP): organization groups, app grants and the check that a
 * member may open an app.
 *
 * A group or grant of another organization, or a deleted one, is reported as not found, never as a
 * security error, so an id does not reveal that it exists elsewhere.
 */
public enum ExceptionDomainAccessError implements ExceptionDomainError {

    // ── Security (APP_SEC) ───────────────────────────────────────────────────
    SEC_APP_ACCESS_DENIED("APP_SEC_001", "You have no access to this app in this organization. Ask an owner or admin of the organization to grant it.", ExceptionCategory.SEC),
    SEC_RESOURCE_ORGANIZATION_MISMATCH("APP_SEC_002", "This item belongs to another organization. Switch to that organization to open it.", ExceptionCategory.SEC),

    // ── Validation (APP_VAL) ────────────────────────────────────────────────
    VAL_GRANT_TARGET_INVALID("APP_VAL_001", "A grant needs exactly one target: a user or a group.", ExceptionCategory.VAL),
    VAL_MEMBER_WITHOUT_APP_ACCESS("APP_VAL_002", "This member has no access to this app in this organization. Ask an owner or admin of the organization to grant it.", ExceptionCategory.VAL),

    // ── State (APP_STA) ─────────────────────────────────────────────────────
    STA_SYSTEM_GROUP_IMMUTABLE("APP_STA_001", "The Everyone group cannot be renamed, deleted or given members.", ExceptionCategory.STA),
    STA_GROUP_NAME_TAKEN("APP_STA_002", "A group with this name already exists in this organization.", ExceptionCategory.STA),

    // ── Not Found (APP_NOT) ──────────────────────────────────────────────────
    NOT_GROUP_FOUND("APP_NOT_001", "Group not found.", ExceptionCategory.NOT),
    NOT_GRANT_FOUND("APP_NOT_002", "App grant not found.", ExceptionCategory.NOT),
    NOT_GROUP_MEMBER_FOUND("APP_NOT_003", "User is not a member of this group.", ExceptionCategory.NOT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainAccessError(String code, String message, ExceptionCategory category) {
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
