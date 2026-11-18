package com.korur.oba.shrine.organization.constant.service;

/**
 * Log message constants for organization access service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationAccessServiceLogConstant {

    private OrganizationAccessServiceLogConstant() {
    }

    // Log message when a member has insufficient permissions.
    public static final String INSUFFICIENT_PERMISSIONS =
            "Insufficient organization permissions: organizationId={}, userId={}, requiredMinimumRole={}";

    // Log message when a disabled organization is accessed.
    public static final String ORGANIZATION_DISABLED =
            "Access denied: organization is disabled: organizationId={}, userId={}";

    // Log message when a member whose membership is not active is refused.
    public static final String MEMBERSHIP_DISABLED =
            "Access denied: organization membership is not active: organizationId={}, userId={}, status={}";
}
