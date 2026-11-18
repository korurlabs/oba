package com.korur.oba.apps.kazan.dailyupdate.exception;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for daily update nudges (DLY).
 *
 * Numeric suffixes are append-only; never renumber an existing code, since the mobile app may
 * already match on it.
 */
public enum ExceptionDomainDailyUpdateError implements ExceptionDomainError {

    // ── Not Found (DLY_NOT) ─────────────────────────────────────────────────
    // Also the answer for a GUEST, a disabled member or a disabled account: none of them is expected
    // to post, so none can be nudged.
    NOT_NUDGE_RECIPIENT_FOUND("DLY_NOT_001", "This person is not a member expected to post an update.", ExceptionCategory.NOT),

    // ── Validation (DLY_VAL) ────────────────────────────────────────────────
    VAL_NUDGE_SELF("DLY_VAL_001", "You cannot nudge yourself.", ExceptionCategory.VAL),
    VAL_NUDGE_RECIPIENT_POSTED("DLY_VAL_002", "This person has already posted today's update.", ExceptionCategory.VAL),

    // ── State (DLY_STA) ─────────────────────────────────────────────────────
    STA_NUDGE_ALREADY_SENT("DLY_STA_001", "Already nudged today.", ExceptionCategory.STA);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainDailyUpdateError(String code, String message, ExceptionCategory category) {
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
