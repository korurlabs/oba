package com.korur.oba.connect.notification.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Notification domain (NTF).
 *
 * <p>Covers email notification delivery, Microsoft Graph integration, and persistence.</p>
 */
public enum ExceptionDomainNotificationError implements ExceptionDomainError {

    // ── Upstream (NTF_UPS) ───────────────────────────────────────────────────
    UPS_MS_GRAPH_COMMUNICATION("NTF_UPS_001", "Failed to communicate with Microsoft Graph API for email delivery.", ExceptionCategory.UPS),
    UPS_EMAIL_SEND_FAILED("NTF_UPS_002", "Failed to send email notification.", ExceptionCategory.UPS),

    // ── Internal (NTF_INT) ───────────────────────────────────────────────────
    INT_EMAIL_PERSISTENCE_FAILED("NTF_INT_001", "Failed to persist email notification record.", ExceptionCategory.INT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainNotificationError(String code, String message, ExceptionCategory category) {
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
