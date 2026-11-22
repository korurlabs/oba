package com.korur.oba.apps.kalkan.core.exception.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes of Kalkan (KLK), shared by its page and permission domains.
 *
 * A page the caller may not see answers NOT_001 like a missing page, so a hidden root is never
 * confirmed. Organization membership and role refusals come from the endpoint gate with platform
 * codes; SEC_001 is only the caller's level on a root, or the ADMIN role a new root needs.
 */
public enum ExceptionDomainKalkanError implements ExceptionDomainError {

    // ── Security (KLK_SEC) ───────────────────────────────────────────────────
    SEC_LEVEL_TOO_LOW("KLK_SEC_001", "Your access level on this page or your organization role is too low for this action.", ExceptionCategory.SEC),
    SEC_ATTACHMENT_DELETE_REFUSED("KLK_SEC_002", "Attachments of a Kalkan page are kept as a record and are never deleted.", ExceptionCategory.SEC),

    // ── Not Found (KLK_NOT) ──────────────────────────────────────────────────
    NOT_PAGE_FOUND("KLK_NOT_001", "Page not found.", ExceptionCategory.NOT),
    NOT_REVISION_FOUND("KLK_NOT_002", "Page revision not found.", ExceptionCategory.NOT),

    // ── State (KLK_STA) ──────────────────────────────────────────────────────
    STA_SLUG_TAKEN("KLK_STA_001", "A page with this slug already exists at this place.", ExceptionCategory.STA),
    STA_VERSION_OUTDATED("KLK_STA_002", "The page was changed meanwhile; reload it and apply your change again.", ExceptionCategory.STA),
    STA_PARENT_DELETED("KLK_STA_003", "The parent page is deleted; restore the parent first.", ExceptionCategory.STA),
    STA_CHANGED_SINCE_PUSH("KLK_STA_004", "The page was changed in Kalkan since the last push.", ExceptionCategory.STA),

    // ── Validation (KLK_VAL) ─────────────────────────────────────────────────
    VAL_PARENT_PATH_MISSING("KLK_VAL_001", "A page of the parent path does not exist.", ExceptionCategory.VAL),
    VAL_SUBJECT_NOT_ACTIVE("KLK_VAL_002", "The subject is not an active member or group of the organization.", ExceptionCategory.VAL),
    VAL_GUEST_ABOVE_VIEW("KLK_VAL_003", "A guest can be given VIEW only.", ExceptionCategory.VAL),
    VAL_MOVE_INVALID("KLK_VAL_004", "The page cannot move into its own subtree, deeper than 10 levels, or across the root boundary.", ExceptionCategory.VAL),
    VAL_DUPLICATE_LOCALE("KLK_VAL_005", "A locale is given more than once.", ExceptionCategory.VAL),
    VAL_PERMISSION_ON_CHILD("KLK_VAL_006", "Permissions are set on root pages only.", ExceptionCategory.VAL),
    VAL_RESERVED_ROOT_SLUG("KLK_VAL_007", "This slug is reserved and cannot name a root page.", ExceptionCategory.VAL),
    VAL_PAGE_NOT_DELETED("KLK_VAL_008", "Page is not deleted and cannot be restored.", ExceptionCategory.VAL);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainKalkanError(String code, String message, ExceptionCategory category) {
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
