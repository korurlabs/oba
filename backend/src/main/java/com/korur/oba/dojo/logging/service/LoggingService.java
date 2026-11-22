package com.korur.oba.dojo.logging.service;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.event.Level;

// 3. Java SDK
import java.util.Arrays;
import java.util.regex.Pattern;

// 4. Internal
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
     * Control characters other than CR and LF, plus NEL and the Unicode line and paragraph
     * separators, all of which a log viewer may treat as a line end.
     */
    private static final Pattern LINE_BREAKING_CHARACTERS =
            Pattern.compile("[\\x00-\\x09\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F\\u2028\\u2029]");

    private static final String REPLACEMENT = "_";

    /**
     * Logs an observe-level message (maps to DEBUG).
     * Used for request lifecycle bookends and processing traces.
     */
    public void observe(LogPrefix prefix, LogLayer layer, String message, Object... params) {
        write(Level.DEBUG, prefix, layer, message, null, params);
    }

    /**
     * Logs an informational message with domain and layer context.
     */
    public void info(LogPrefix prefix, LogLayer layer, String message, Object... params) {
        write(Level.INFO, prefix, layer, message, null, params);
    }

    /**
     * Logs a warning message with domain and layer context.
     */
    public void warn(LogPrefix prefix, LogLayer layer, String message, Object... params) {
        write(Level.WARN, prefix, layer, message, null, params);
    }

    /**
     * Logs an error message with exception, domain and layer context.
     */
    public void error(LogPrefix prefix, LogLayer layer, String message, Throwable throwable, Object... params) {
        write(Level.ERROR, prefix, layer, message, throwable, params);
    }

    /**
     * Logs an error message with domain and layer context (without exception).
     */
    public void error(LogPrefix prefix, LogLayer layer, String message, Object... params) {
        write(Level.ERROR, prefix, layer, message, null, params);
    }

    /**
     * Logs a debug message (maps to TRACE) with domain and layer context.
     */
    public void debug(LogPrefix prefix, LogLayer layer, String message, Object... params) {
        write(Level.TRACE, prefix, layer, message, null, params);
    }

    /**
     * Writes one log event with the message and every argument neutralised.
     *
     * Arguments routinely carry request data (file names, headers, free text). A line break
     * in one of them would let a caller end the entry early and forge a second one. Every
     * argument is therefore rendered by {@link #renderArgumentForLog(Object)} and the message
     * passed through {@link #replaceLineBreaks(String)}, so no caller has to remember it. A
     * trailing Throwable argument stays the event's cause, as SLF4J treats it, so its stack
     * trace is still printed; the cause is set through the fluent API because the stack trace
     * is a structured field of the encoder, not part of the message line.
     */
    private void write(Level level, LogPrefix prefix, LogLayer layer, String message,
                       Throwable throwable, Object[] params) {
        checkLogArguments(prefix, layer);
        if (!log.isEnabledForLevel(level)) {
            return;
        }
        Object[] args = params == null ? new Object[0] : params;
        Throwable cause = throwable;
        int argCount = args.length;
        if (cause == null && argCount > 0 && args[argCount - 1] instanceof Throwable trailingCause) {
            cause = trailingCause;
            argCount--;
        }
        Object[] safeArgs = new Object[argCount];
        for (int i = 0; i < argCount; i++) {
            safeArgs[i] = renderArgumentForLog(args[i]);
        }
        try {
            setAppContext(prefix, layer);
            log.atLevel(level).setCause(cause).log(replaceLineBreaks(message), safeArgs);
        } finally {
            clearAppContext();
        }
    }

    /**
     * Renders one argument to text the way SLF4J would and passes it through
     * {@link #replaceLineBreaks(String)}. Null stays null so the placeholder still reads "null".
     */
    private static String renderArgumentForLog(Object value) {
        if (value == null) {
            return null;
        }
        String text = value instanceof Object[] array ? Arrays.deepToString(array) : String.valueOf(value);
        return replaceLineBreaks(text);
    }

    /**
     * Replaces every character that can end or split a log line with an underscore: the ASCII
     * and C1 control characters (among them NEL, U+0085) and the Unicode line and paragraph
     * separators first, then CR and LF. CR and LF are replaced by plain String.replace calls
     * last, the form static analysis recognises as removing line breaks from logged values.
     */
    private static String replaceLineBreaks(String value) {
        if (value == null) {
            return null;
        }
        return LINE_BREAKING_CHARACTERS.matcher(value).replaceAll(REPLACEMENT)
                .replace("\r", REPLACEMENT)
                .replace("\n", REPLACEMENT);
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
