package com.korur.oba.dojo.exception.enums;

// 1. Framework-level
import org.springframework.http.HttpStatus;

// 2. Third-party
import lombok.Getter;

/**
 * Defines the fixed set of exception categories in the system.
 *
 * <p>Each category maps to exactly one HTTP status code. Exception subclasses
 * enforce that only error codes with a matching category can be used,
 * preventing misclassification at runtime.</p>
 *
 * <p>Category selection flowchart:</p>
 * <ol>
 *   <li>Is the user authenticated? No token / bad token → {@code AUT}</li>
 *   <li>Is the user authorized? Wrong role / not member / not owner → {@code SEC}</li>
 *   <li>Does the resource exist? Entity not found in DB → {@code NOT}</li>
 *   <li>Is the request data valid? Format, missing field, business rule → {@code VAL}</li>
 *   <li>Does current system state prevent the action? Duplicate, lifecycle conflict, concurrency → {@code STA}</li>
 *   <li>Did an external system fail? Third-party API error → {@code UPS}</li>
 *   <li>Is this a bug? Should be impossible → {@code INT}</li>
 *   <li>DB constraint violation? Unique / FK blew up → {@code DAT}</li>
 * </ol>
 */
@Getter
public enum ExceptionCategory {

    /** Authentication failed — token missing, expired, or invalid. */
    AUT(HttpStatus.UNAUTHORIZED),

    /** Authorization / access denied — wrong role, not a member, ownership check failed. */
    SEC(HttpStatus.FORBIDDEN),

    /** Resource not found — entity lookup returned empty. */
    NOT(HttpStatus.NOT_FOUND),

    /** Validation — business rule violations, semantic data errors. */
    VAL(HttpStatus.UNPROCESSABLE_ENTITY),

    /** State conflict — duplicates, lifecycle violations, concurrency conflicts. */
    STA(HttpStatus.CONFLICT),

    /** API boundary — malformed request, wrong content type, missing headers, rate limiting. */
    API(HttpStatus.BAD_REQUEST),

    /** Upstream — external dependency failure (third-party API down, timeout, bad response). */
    UPS(HttpStatus.BAD_GATEWAY),

    /** Data integrity — persistence / database constraint violation. */
    DAT(HttpStatus.INTERNAL_SERVER_ERROR),

    /** Internal — bug, unreachable code, programming error. */
    INT(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ExceptionCategory(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }
}
