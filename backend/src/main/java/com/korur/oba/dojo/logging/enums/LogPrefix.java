package com.korur.oba.dojo.logging.enums;

// 1. Framework-level

// 2. Third-party
import lombok.Getter;

// 3. Java SDK
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Derives a dotted domain path from the calling class's package name.
 *
 * <p>Format: {@code module.domain}</p>
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code com.korur.oba.dojo.security.*}       → {@code dojo.security}</li>
 *   <li>{@code com.korur.oba.shrine.identity.*}     → {@code shrine.identity}</li>
 *   <li>{@code com.korur.oba.connect.anthropic.*}   → {@code connect.anthropic}</li>
 *   <li>{@code com.korur.oba.apps.kaizen.ticket.*}  → {@code kaizen.ticket}</li>
 * </ul>
 *
 * <p>The domain is stored as an MDC field in JSON structured logging output.</p>
 */
@Getter
public final class LogPrefix {

    private static final String BASE_PACKAGE = "com.korur.oba.";
    private static final String APPS_MODULE = "apps";
    private static final Map<String, LogPrefix> CACHE = new ConcurrentHashMap<>();

    private final String domain;

    private LogPrefix(String domain) {
        this.domain = domain;
    }

    /**
     * Resolves a log prefix from the calling class's package.
     * Results are cached per class name for zero repeated overhead.
     *
     * <p>Usage: {@code private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MyService.class);}</p>
     */
    public static LogPrefix fromClass(Class<?> clazz) {
        return CACHE.computeIfAbsent(clazz.getName(), key -> {
            String packageName = clazz.getPackageName();
            return new LogPrefix(resolveFromPackage(packageName));
        });
    }

    private static String resolveFromPackage(String packageName) {
        if (!packageName.startsWith(BASE_PACKAGE)) {
            return "unknown";
        }

        String relative = packageName.substring(BASE_PACKAGE.length());
        String[] parts = relative.split("\\.");

        if (parts.length == 0) {
            return "unknown";
        }

        String module = parts[0];

        // apps.{app}.{area}... → app.area, such as kaizen.ticket, so each app's domains stay distinguishable
        if (APPS_MODULE.equals(module) && parts.length >= 3) {
            return parts[1] + "." + parts[2];
        }

        // {module}.{area}... → module.area, such as dojo.security or connect.github
        if (parts.length >= 2) {
            return module + "." + parts[1];
        }

        return module;
    }
}
