package com.korur.oba.dojo.exception.constant;

/**
 * Central place to hold commonly used exception-related constants.
 */
public final class ExceptionConstant {

    private ExceptionConstant() {
    }

    public static final String ERROR = "error";
    public static final String ERROR_REASON = "reason";
    public static final String ERROR_ACTION = "action";
    public static final String EXPECTED = "expected";
    public static final String ACTUAL = "actual";
    public static final String MESSAGE = "message";
    public static final String CODE = "code";
    public static final String DETAILS = "details";
    public static final String TIMESTAMP = "timestamp";
    public static final String FILE_NAME = "filename";

    // Map keys for error details
    public static final String FIELD_PATH = "path";

    // Client-facing response messages (used in CustomResponse.message)
    public static final String MSG_VALIDATION_ERROR = "One or more fields failed validation. Please check the error details and try again.";
    public static final String MSG_MALFORMED_JSON = "The request body contains malformed JSON. Please verify the syntax and try again.";
    public static final String MSG_INVALID_FIELD_VALUE = "A field in the request body has an invalid value. Please check the error details.";
    public static final String MSG_INVALID_FILE_UPLOAD = "The uploaded file could not be processed. Please verify the file format and size.";
    public static final String MSG_FILE_TOO_LARGE = "The uploaded file exceeds the maximum allowed size. Please reduce the file size and try again.";
    public static final String MSG_UNSUPPORTED_CONTENT_TYPE = "The request content type is not supported. Please use a supported media type.";
    public static final String MSG_TYPE_MISMATCH = "A request parameter has an invalid type. Please check the expected format.";
    public static final String MSG_MISSING_PARAMETER = "A required request parameter is missing. Please include it and try again.";
    public static final String MSG_MISSING_HEADER = "A required request header is missing. Please include it and try again.";
    public static final String MSG_METHOD_NOT_ALLOWED = "The HTTP method is not supported for this endpoint. Please check the allowed methods.";
    public static final String MSG_CONCURRENT_UPDATE = "This record was changed by another request at the same time. Please reload it and try again.";
    public static final String MSG_DATABASE_TIMEOUT = "The data could not be read in time. Please try again in a moment.";

    // Client-facing error detail values (inside errorValues maps)
    public static final String MSG_INVALID_ENUM_VALUE = "The provided enum value is not a valid option for this field.";
    public static final String MSG_INVALID_VALUE = "The provided value is not valid for this field. Please check the expected format.";

    // Internal fallbacks (used when field name extraction fails)
    public static final String FALLBACK_UNKNOWN_FIELD = "unknown";
    public static final String FALLBACK_VALIDATION_FAILED = "Validation failed";
    public static final String FALLBACK_PARAM_PREFIX = "param";
}
