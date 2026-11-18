package com.korur.oba.dojo.logging.enums;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Architectural layer labels for structured logging.
 *
 * <p>Values are lowercase strings stored as the {@code layer} MDC field
 * in JSON log output.</p>
 */
@Getter
@AllArgsConstructor
public enum LogLayer {
    CONTROLLER("controller"),
    SERVICE("service"),
    HELPER("helper"),
    HANDLER("handler"),
    VALIDATOR("validator"),
    FILTER("filter"),
    CLIENT("client"),
    SCHEDULER("scheduler"),
    CONFIG("config");

    private final String layer;
}
