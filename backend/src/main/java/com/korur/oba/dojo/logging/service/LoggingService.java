package com.korur.oba.dojo.logging.service;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.logging.constant.LoggingConstant;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;

/**
 * A centralized logging service that standardizes application log messages.
 *
 * <p>Request/user correlation fields come from MDC (set by filters).
 * Application context ({@code domain}, {@code layer}) is set per log call.</p>
 *
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MyService.class);
 *
 * // Observe-level logging (request bookends)
 * loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RECEIVED_REQUEST);
 *
 * // Info logging
 * loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VAULT_CREATED, id);
 *
 * // Error logging with exception
 * loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ERROR_MESSAGE, exception, entityId);
 * }</pre>
 */
@Service
@Slf4j
public class LoggingService {

    /**
     * Logs an observe-level message (maps to DEBUG).
     * Used for request lifecycle bookends and processing traces.
     */
    public void observe(LogPrefix prefix, LogLayer layer, String message, Object... params) {
        checkLogArguments(prefix, layer);
        try {
            setAppContext(prefix, layer);
            log.debug(message, params);
        } finally {
            clearAppContext();
        }
    }

    /**
     * Logs an informational message with domain and layer context.
     */
    public void info(LogPrefix prefix, LogLayer layer, String message, Object... params) {
        checkLogArguments(prefix, layer);
        try {
            setAppContext(prefix, layer);
            log.info(message, params);
        } finally {
            clearAppContext();
        }
    }

    /**
     * Logs a warning message with domain and layer context.
     */
    public void warn(LogPrefix prefix, LogLayer layer, String message, Object... params) {
        checkLogArguments(prefix, layer);
        try {
            setAppContext(prefix, layer);
            log.warn(message, params);
        } finally {
            clearAppContext();
        }
    }

    /**
     * Logs an error message with exception, domain and layer context.
     */
    public void error(LogPrefix prefix, LogLayer layer, String message, Throwable throwable, Object... params) {
        checkLogArguments(prefix, layer);
        try {
            setAppContext(prefix, layer);
            log.error(message, params, throwable);
        } finally {
            clearAppContext();
        }
    }

    /**
     * Logs an error message with domain and layer context (without exception).
     */
    public void error(LogPrefix prefix, LogLayer layer, String message, Object... params) {
        checkLogArguments(prefix, layer);
        try {
            setAppContext(prefix, layer);
            log.error(message, params);
        } finally {
            clearAppContext();
        }
    }

    /**
     * Logs a debug message (maps to TRACE) with domain and layer context.
     */
    public void debug(LogPrefix prefix, LogLayer layer, String message, Object... params) {
        checkLogArguments(prefix, layer);
        try {
            setAppContext(prefix, layer);
            log.trace(message, params);
        } finally {
            clearAppContext();
        }
    }

    private void checkLogArguments(LogPrefix prefix, LogLayer layer) {
        if (prefix == null || layer == null) {
            throw new InternalServerErrorException(ExceptionInternalError.UNEXPECTED);
        }
    }

    private void setAppContext(LogPrefix prefix, LogLayer layer) {
        MDC.put(LoggingConstant.KEY_DOMAIN, prefix.getDomain());
        MDC.put(LoggingConstant.KEY_LAYER, layer.getLayer());
    }

    private void clearAppContext() {
        MDC.remove(LoggingConstant.KEY_DOMAIN);
        MDC.remove(LoggingConstant.KEY_LAYER);
    }
}
