package com.korur.oba.connect.push.exception;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the push notification connect domain (PSH).
 *
 * Numeric suffixes are append-only; never renumber an existing code, since the mobile app may
 * already match on it.
 */
public enum ExceptionDomainPushError implements ExceptionDomainError {

    // ── Not Found (PSH_NOT) ─────────────────────────────────────────────────
    // Also the answer for another user's device, so a caller cannot learn which device ids exist.
    NOT_PUSH_DEVICE_FOUND("PSH_NOT_001", "Push device not found.", ExceptionCategory.NOT),
    // The user's preference row disappeared between its creation and its update, which happens only
    // when the user is removed at that moment.
    NOT_PUSH_PREFERENCE_FOUND("PSH_NOT_002", "Push preferences not found.", ExceptionCategory.NOT),

    // ── Upstream (PSH_UPS) ──────────────────────────────────────────────────
    // Expo answered 5xx or 429, or could not be reached; the call is retried.
    UPS_EXPO_UNAVAILABLE("PSH_UPS_001", "Expo push service is unavailable.", ExceptionCategory.UPS),

    // ── Internal (PSH_INT) ──────────────────────────────────────────────────
    // Expo refused the request itself (4xx other than 429) or answered with a body that cannot be
    // read: a wrong access token or a malformed message, which a retry cannot fix.
    INT_EXPO_REQUEST_REJECTED("PSH_INT_001", "Expo push service rejected the request.", ExceptionCategory.INT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainPushError(String code, String message, ExceptionCategory category) {
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
