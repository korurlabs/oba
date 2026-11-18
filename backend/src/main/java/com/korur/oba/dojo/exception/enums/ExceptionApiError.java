package com.korur.oba.dojo.exception.enums;

/**
 * Purpose:
 * Defines generic, cross-cutting error codes for request-level problems (GEN_API_*).
 * These codes represent issues with the HTTP request itself — not with business logic.
 *
 * Includes codes for: malformed JSON, missing parameters, missing headers,
 * unsupported media types, rate limiting, and file size violations.
 * All codes in this enum belong to the API category and are thrown via
 * BadRequestException or TooManyRequestsException.
 *
 * Bigger picture:
 * This is the error code enum for the API category. It pairs with BadRequestException
 * (the throwable type). The distinction from ExceptionValidationError is important:
 * ExceptionApiError codes signal that the request was structurally invalid (HTTP 400
 * Bad Request), while ExceptionValidationError codes signal that the request was
 * well-formed but failed a business rule (HTTP 422 Unprocessable Entity). The different
 * HTTP status codes allow frontends and monitoring to distinguish them without parsing
 * error code prefixes.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2025-03-01
 * @updated 2026-03-07
 */
public enum ExceptionApiError implements ExceptionError {

    BAD_REQUEST("GEN_API_001", "Bad request. Please check the input parameters."),
    UNSUPPORTED_MEDIA_TYPE("GEN_API_002", "Unsupported media type provided in request."),
    UNSUPPORTED_CONTENT_TYPE("GEN_API_003", "Content-Type is not supported or file is invalid."),
    TOO_MANY_REQUESTS("GEN_API_004", "Too many requests."),
    MISSING_PROPERTY("GEN_API_005", "Required property is missing from the request."),
    METHOD_NOT_ALLOWED("GEN_API_006", "HTTP method not allowed for this endpoint."),
    MISSING_HEADER("GEN_API_007", "Required request header is missing."),
    FILE_TOO_LARGE("GEN_API_008", "Uploaded file exceeds the maximum allowed size.");

    private final String code;
    private final String message;

    ExceptionApiError(String code, String message) {
        this.code = code;
        this.message = message;
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
        return ExceptionCategory.API;
    }
}
