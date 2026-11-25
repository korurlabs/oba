package com.korur.oba.connect.routine.core.enums;

/**
 * Outcome of one fire of a hosted routine, as the vendor answered it.
 */
public enum RoutineFireStatus {
    // The vendor accepted the fire and started a session.
    FIRED,
    // No session started: the vendor failed or timed out, or the routine id was malformed.
    FAILED,
    // The vendor refused the fire because the routine or the account reached its fire limit.
    RATE_LIMITED,
    // The vendor refused the secret, usually because it was rotated at the vendor.
    UNAUTHORIZED
}
