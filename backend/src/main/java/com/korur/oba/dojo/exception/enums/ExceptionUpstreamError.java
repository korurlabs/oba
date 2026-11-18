package com.korur.oba.dojo.exception.enums;

/**
 * Upstream and external dependency error codes ({@code GEN_UPS_*}).
 *
 * <p>These are generic, cross-cutting upstream errors not tied to any specific domain.</p>
 */
public enum ExceptionUpstreamError implements ExceptionError {

    BAD_GATEWAY("GEN_UPS_001", "Bad gateway error."),
    GATEWAY_TIMEOUT("GEN_UPS_002", "The gateway has timed out."),
    UNREACHABLE("GEN_UPS_003",
            "A required upstream dependency could not be reached. This indicates network, DNS, or service availability issues.");

    private final String code;
    private final String message;

    ExceptionUpstreamError(String code, String message) {
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
        return ExceptionCategory.UPS;
    }
}
