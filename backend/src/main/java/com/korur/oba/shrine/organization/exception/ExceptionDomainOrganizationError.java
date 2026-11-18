package com.korur.oba.shrine.organization.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Organization domain (ORG).
 *
 * Covers multi-tenant membership, organization security, and member lookups.
 */
public enum ExceptionDomainOrganizationError implements ExceptionDomainError {

    // ── Security (ORG_SEC) ───────────────────────────────────────────────────
    SEC_USER_NOT_MEMBER("ORG_SEC_001", "User is not a member of this organization.", ExceptionCategory.SEC),
    SEC_MEMBERSHIP_DISABLED("ORG_SEC_002", "User's membership in this organization is disabled.", ExceptionCategory.SEC),
    SEC_INSUFFICIENT_PERMISSIONS("ORG_SEC_003", "User does not have sufficient permissions for this operation.", ExceptionCategory.SEC),
    SEC_CANNOT_UPDATE_OWN_ROLE("ORG_SEC_004", "Users cannot update their own role.", ExceptionCategory.SEC),
    SEC_ORGANIZATION_DISABLED("ORG_SEC_005", "This organization is currently disabled.", ExceptionCategory.SEC),
    SEC_ADMIN_CANNOT_MODIFY_OWNER("ORG_SEC_006", "Admins cannot modify an owner's membership.", ExceptionCategory.SEC),

    // ── Validation (ORG_VAL) ────────────────────────────────────────────────
    VAL_OWNER_ROLE_NOT_ASSIGNABLE("ORG_VAL_001", "The OWNER role cannot be assigned.", ExceptionCategory.VAL),
    VAL_INVALID_ORGANIZATION_STATUS("ORG_VAL_002", "Invalid target organization status. Allowed values: ACTIVE, DISABLED.", ExceptionCategory.VAL),
    VAL_INVALID_MEMBER_STATUS("ORG_VAL_003", "Invalid target member status. Allowed values: ACTIVE, DISABLED.", ExceptionCategory.VAL),

    // ── API (ORG_API) ───────────────────────────────────────────────────────
    API_INVALID_ORGANIZATION_ID("ORG_API_001", "X-Organization-ID header must be a valid UUID.", ExceptionCategory.API),
    API_ORGANIZATION_ID_MISMATCH("ORG_API_002", "The organization in the path and the X-Organization-ID header differ.", ExceptionCategory.API),

    // ── State (ORG_STA) ─────────────────────────────────────────────────────
    STA_MEMBER_ALREADY_DISABLED("ORG_STA_001", "Organization member is already disabled.", ExceptionCategory.STA),
    STA_MEMBER_NOT_DISABLED("ORG_STA_002", "Organization member is not disabled.", ExceptionCategory.STA),
    STA_SOLE_OWNER_CANNOT_LEAVE("ORG_STA_003", "Cannot leave organization as the sole owner. Transfer ownership first.", ExceptionCategory.STA),
    STA_MEMBER_NOT_SOFT_DELETED("ORG_STA_004", "Organization member is not soft-deleted.", ExceptionCategory.STA),
    STA_ORG_ALREADY_DISABLED("ORG_STA_005", "Organization is already disabled.", ExceptionCategory.STA),
    STA_ORG_NOT_DISABLED("ORG_STA_006", "Organization is not disabled.", ExceptionCategory.STA),
    // ── Not Found (ORG_NOT) ──────────────────────────────────────────────────
    NOT_ORGANIZATION_FOUND("ORG_NOT_001", "Organization not found.", ExceptionCategory.NOT),
    NOT_ORGANIZATION_MEMBER_FOUND("ORG_NOT_002", "Organization member not found.", ExceptionCategory.NOT),
    NOT_DELETED_MEMBER_FOUND("ORG_NOT_003", "No soft-deleted membership found for this user in this organization.", ExceptionCategory.NOT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainOrganizationError(String code, String message, ExceptionCategory category) {
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
