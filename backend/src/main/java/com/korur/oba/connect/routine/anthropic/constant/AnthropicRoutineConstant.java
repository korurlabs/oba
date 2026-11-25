package com.korur.oba.connect.routine.anthropic.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Duration;
import java.util.regex.Pattern;

// 4. Internal

/**
 * Endpoint, headers, defaults and the routine id format of Anthropic routines.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AnthropicRoutineConstant {

    private AnthropicRoutineConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String PROPERTIES_PREFIX = "oba.integrations.routine.anthropic";

    // --- Defaults, used when the properties leave a key out ---
    public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    // Routines are a research preview: the fire endpoint answers only with this beta header.
    public static final String DEFAULT_BETA_HEADER = "experimental-cc-routine-2026-04-01";
    public static final String DEFAULT_VERSION = "2023-06-01";
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);

    // --- Request ---
    public static final String FIRE_PATH = "/v1/claude_code/routines/{routineId}/fire";
    public static final String HEADER_BETA = "anthropic-beta";
    public static final String HEADER_VERSION = "anthropic-version";

    // The id is placed in the request path, so the pattern also keeps any other path out of it.
    public static final Pattern ROUTINE_ID_PATTERN = Pattern.compile("trig_[A-Za-z0-9]{1,60}");
}
