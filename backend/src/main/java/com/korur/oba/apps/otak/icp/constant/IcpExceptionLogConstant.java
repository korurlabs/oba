package com.korur.oba.apps.otak.icp.constant;

/**
 * Log message constants for ICP domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class IcpExceptionLogConstant {

    private IcpExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for an ICP that the organization does not have, missing or of another organization.
     */
    public static final String ICP_NOT_FOUND = "ICP not found: id={}, organizationId={}";

    /**
     * Log message for a minimum company size above the maximum.
     */
    public static final String ICP_SIZE_RANGE_INVALID = "ICP size range refused, minimum above maximum: min={}, max={}";

    /**
     * Log message for a name another ICP of the organization already has, case ignored.
     */
    public static final String ICP_NAME_TAKEN = "ICP name refused, already taken: organizationId={}, name={}";
}
