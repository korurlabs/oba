package com.korur.oba.dojo.logging.mdc;

// 1. Framework-level

// 2. Third-party
import org.slf4j.MDC;

// 3. Java SDK
import java.util.Set;

// 4. Internal
import com.korur.oba.dojo.logging.constant.LoggingConstant;
import com.korur.oba.dojo.util.SecurityUtils;

/**
 * Centralized MDC access for logging context.
 *
 * <p>Application code should use this helper instead of directly calling {@link MDC},
 * so managed keys are consistent and can be safely cleaned up after each request.</p>
 *
 * <p>Key names and default values are defined in {@link LoggingConstant}.</p>
 */
public final class LoggingMdc {

    private LoggingMdc() {
        // Utility class
    }

    private static final Set<String> MANAGED_KEYS = Set.of(
            LoggingConstant.KEY_REQUEST_ID,
            LoggingConstant.KEY_KEYCLOAK_ID,
            LoggingConstant.KEY_ORGANIZATION_ID,
            LoggingConstant.KEY_ISSUER_REALM,
            LoggingConstant.KEY_DOMAIN,
            LoggingConstant.KEY_LAYER
    );

    /**
     * Puts a managed key into MDC after log-sanitization.
     *
     * @param key   one of the managed keys defined in {@link LoggingConstant}
     * @param value value to store; null removes the key
     */
    public static void putManaged(String key, String value) {
        if (!MANAGED_KEYS.contains(key)) {
            throw new IllegalArgumentException("Unsupported MDC key: " + key);
        }

        if (value == null) {
            MDC.remove(key);
            return;
        }

        MDC.put(key, SecurityUtils.sanitizeForLog(value));
    }

    /**
     * Reads a value from MDC.
     *
     * @param key MDC key
     * @return current value or null
     */
    public static String get(String key) {
        return MDC.get(key);
    }

    /**
     * Removes a managed MDC key if present.
     *
     * @param key managed key
     */
    public static void removeManaged(String key) {
        if (!MANAGED_KEYS.contains(key)) {
            return;
        }
        MDC.remove(key);
    }

    /**
     * Removes all managed MDC keys owned by logging infrastructure.
     */
    public static void clearManagedContext() {
        for (String key : MANAGED_KEYS) {
            MDC.remove(key);
        }
    }
}
