package com.korur.oba.connect.realtime.exception;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the realtime connect domain (RTM).
 *
 * The codes travel as the message header of a STOMP ERROR frame, not in an HTTP error body, and
 * the clients key their reconnect behaviour on them: RTM_SEC_001
 * ({@link #SEC_REALTIME_UNAUTHENTICATED}) means "reconnect at once with a new ticket", RTM_SEC_002
 * ({@link #SEC_REALTIME_FORBIDDEN}) means "this session is not allowed, show the organization
 * error". RTM_SEC_002 is also the HTTP 403 of a ticket request whose access token carries no expiry,
 * because a session bound to such a token could never be ended by its expiry.
 */
public enum ExceptionDomainRealtimeError implements ExceptionDomainError {

    // ── Security (RTM_SEC) ──────────────────────────────────────────────────
    SEC_REALTIME_UNAUTHENTICATED("RTM_SEC_001",
            "The realtime session needs a valid handshake ticket and an unexpired access token.", ExceptionCategory.SEC),
    SEC_REALTIME_FORBIDDEN("RTM_SEC_002",
            "The realtime session is not allowed for this user, organization or destination.", ExceptionCategory.SEC);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainRealtimeError(String code, String message, ExceptionCategory category) {
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
