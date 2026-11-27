package com.korur.oba.apps.kaya.machine.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the machine services. A token is never logged, only its id and prefix.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MachineServiceLogConstant {

    private MachineServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_MACHINES = "Listing machines of organization {} with {}";
    public static final String READING_MACHINE = "Reading machine {} of organization {}";
    public static final String MACHINE_NOT_FOUND = "Machine {} not found in organization {}";
    public static final String MACHINE_USER_NOT_ACTIVE = "Refused: the agent of machine {} of organization {} is not active";

    // --- Access ---
    public static final String SELF_REQUIRED = "Refused: user {} is not user {}, which owns machine {}";

    // --- Writes ---
    public static final String CREATING_MACHINE = "User {} is registering a machine for user {} of organization {}";
    public static final String MACHINE_CREATED = "Registered machine {} for user {} of organization {} with token {}";
    public static final String MACHINE_REACTIVATED = "Registered retired machine {} of user {} of organization {} again with token {}";
    public static final String MACHINE_NAME_DUPLICATE = "Refused: user {} of organization {} already has the active machine {} with this machine name";
    public static final String MACHINE_RETIRED_REFUSED = "Refused: machine {} of organization {} is retired";
    public static final String ROTATING_TOKEN = "User {} is rotating the token of machine {} of organization {}";
    public static final String TOKEN_ROTATED = "Issued token {} to machine {} of organization {}, revoking {}";
    public static final String REVOKING_TOKEN = "User {} is revoking the token of machine {} of organization {}";
    public static final String TOKEN_REVOKED = "Revoked token {} of machine {} of organization {}";
    public static final String NO_TOKEN_TO_REVOKE = "Machine {} of organization {} has no live token; nothing revoked";
    public static final String RETIRING_MACHINE = "User {} is retiring machine {} of organization {}";
    public static final String MACHINE_RETIRED = "Retired machine {} of organization {}, revoking token {}";
    public static final String MACHINE_ALREADY_RETIRED = "Machine {} of organization {} is already retired";

    // --- User actions ---
    public static final String REVOKING_ALL = "Revoking every token and retiring every machine of user {} in organization {}";
    public static final String ALL_REVOKED = "Revoked {} tokens and retired {} machines of user {} in organization {}";
    public static final String RETIRING_ALL = "Retiring every machine of user {} in organization {} and revoking its token";

    // --- Recorder contact ---
    public static final String MACHINE_SEEN = "Machine {} reported Claude Code {} and account {}";
    public static final String RECORDING_USAGE = "Recording the usage snapshot of machine {} captured at {} from session {}";
    public static final String USAGE_RECORDED = "Stored the usage snapshot of machine {} captured at {}";
    public static final String USAGE_OLDER_KEPT = "Kept the newer usage snapshot of machine {}; the one captured at {} is older";
    public static final String USAGE_WITHOUT_WINDOW = "Usage snapshot of machine {} captured at {} has no window; nothing stored";
    public static final String USAGE_OUT_OF_RANGE = "Refused: usage window {} of machine {} is out of range: {}";
    public static final String READING_USAGE = "Reading the usage of {} machines of organization {}";
    public static final String READING_ACCOUNT_USAGE = "Reading the usage of {} accounts of organization {}";
}
