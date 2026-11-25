package com.korur.oba.apps.kaya.machine.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits, token format, validation messages and error value keys of the machine domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MachineConstant {

    private MachineConstant() {
    }

    // --- Entity fields (specifications and sorting) ---
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_USER = "user";
    public static final String FIELD_ACCOUNT = "account";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_MACHINE_NAME = "machineName";
    public static final String FIELD_USER_FIRST_NAME = "user.profile.firstName";
    public static final String FIELD_USER_LAST_NAME = "user.profile.lastName";
    public static final String FIELD_USER_EMAIL = "user.email";

    // --- Error value and audit detail keys ---
    public static final String FIELD_MACHINE_ID = "machineId";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_TOKEN_ID = "tokenId";
    public static final String FIELD_TOKEN_PREFIX = "tokenPrefix";
    public static final String FIELD_EXPIRES_AT = "expiresAt";
    public static final String FIELD_REVOKED_TOKEN_ID = "revokedTokenId";
    public static final String FIELD_REACTIVATED = "reactivated";
    public static final String FIELD_WINDOW = "window";
    public static final String FIELD_USED_PERCENTAGE = "usedPercentage";
    public static final String FIELD_RESETS_AT = "resetsAt";
    public static final String FIELD_CAPTURED_AT = "capturedAt";
    public static final String WINDOW_FIVE_HOUR = "fiveHour";
    public static final String WINDOW_SEVEN_DAY = "sevenDay";

    // --- Limits ---
    public static final int MACHINE_NAME_MAX_LENGTH = 255;

    // --- Recorder contact ---
    // lastSeenAt moves at most once a minute, so steady batches do not rewrite the machine row on every
    // request.
    public static final long SEEN_REFRESH_SECONDS = 60;

    // --- Usage limits ---
    public static final int USAGE_PERCENTAGE_MAX = 100;
    public static final int USAGE_PERCENTAGE_SCALE = 2;
    // A window resets within 7 days; one more day absorbs clock skew between machine and server.
    public static final long USAGE_RESETS_AT_MAX_DAYS = 8;

    // --- Validation messages ---
    public static final String MACHINE_NAME_REQUIRED_MESSAGE = "Machine name is required";
    public static final String MACHINE_NAME_SIZE_MESSAGE = "Machine name must not exceed 255 characters";
    public static final String STATUS_REQUIRED_MESSAGE = "Status is required";
    public static final String STATUS_RETIRED_ONLY_MESSAGE = "Status can only be set to RETIRED";
    public static final String USAGE_PERCENTAGE_REQUIRED_MESSAGE = "Used percentage is required";
    public static final String USAGE_RESETS_AT_REQUIRED_MESSAGE = "Reset time is required";
    public static final String USAGE_CAPTURED_AT_REQUIRED_MESSAGE = "Capture time is required";
}
