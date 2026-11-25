package com.korur.oba.connect.routine.core.exception;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the routine connector (RTN).
 *
 * A vendor failure during a fire is not an error here: it is a
 * {@link com.korur.oba.connect.routine.core.enums.RoutineFireStatus}. These codes cover a malformed
 * routine id and a connector wired wrongly, which stops the application at start.
 */
public enum ExceptionDomainRoutineError implements ExceptionDomainError {

    // ── Validation (RTN_VAL) ────────────────────────────────────────────────
    VAL_ROUTINE_ID_FORMAT("RTN_VAL_001", "The routine id does not have the format of the routine vendor.",
            ExceptionCategory.VAL),

    // ── Internal / Configuration (RTN_INT) ──────────────────────────────────
    INT_CLIENT_MISSING("RTN_INT_001", "No routine client is registered for the routine vendor.",
            ExceptionCategory.INT),
    INT_CLIENT_DUPLICATE("RTN_INT_002", "More than one routine client is registered for the routine vendor.",
            ExceptionCategory.INT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainRoutineError(String code, String message, ExceptionCategory category) {
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
