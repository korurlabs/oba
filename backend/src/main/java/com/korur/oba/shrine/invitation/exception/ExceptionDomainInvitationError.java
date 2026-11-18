package com.korur.oba.shrine.invitation.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Invitation domain (INV).
 *
 * <p>Covers organization invitation lifecycle, validation, and token lookups.</p>
 */
public enum ExceptionDomainInvitationError implements ExceptionDomainError {

    // ── Validation (INV_VAL) ─────────────────────────────────────────────────
    VAL_ALREADY_MEMBER("INV_VAL_001", "User is already a member of this organization.", ExceptionCategory.VAL),
    VAL_PENDING_INVITATION_EXISTS("INV_VAL_002", "A pending invitation already exists for this email.", ExceptionCategory.VAL),
    VAL_INVITATION_EXPIRED("INV_VAL_003", "This invitation has expired.", ExceptionCategory.VAL),
    VAL_INVITATION_ALREADY_ACCEPTED("INV_VAL_004", "This invitation has already been accepted.", ExceptionCategory.VAL),
    VAL_INVITATION_REVOKED("INV_VAL_005", "This invitation has been revoked.", ExceptionCategory.VAL),
    VAL_EMAIL_MISMATCH("INV_VAL_006", "The email in your account does not match the invitation email.", ExceptionCategory.VAL),
    VAL_CANNOT_INVITE_SELF("INV_VAL_007", "You cannot invite yourself to an organization.", ExceptionCategory.VAL),
    VAL_CANNOT_INVITE_AS_OWNER("INV_VAL_008", "Cannot invite a user with OWNER role.", ExceptionCategory.VAL),
    VAL_INVITATION_DECLINED("INV_VAL_009", "This invitation has been declined.", ExceptionCategory.VAL),
    VAL_INVITATION_NOT_PENDING("INV_VAL_010", "Invitation is not in a pending state.", ExceptionCategory.VAL),

    // ── Not Found (INV_NOT) ──────────────────────────────────────────────────
    NOT_INVITATION_FOUND("INV_NOT_001", "Invitation not found.", ExceptionCategory.NOT),
    NOT_INVITATION_TOKEN_FOUND("INV_NOT_002", "Invitation token not found or invalid.", ExceptionCategory.NOT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainInvitationError(String code, String message, ExceptionCategory category) {
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
