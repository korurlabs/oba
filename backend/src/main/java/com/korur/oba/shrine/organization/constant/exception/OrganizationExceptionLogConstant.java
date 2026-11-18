package com.korur.oba.shrine.organization.constant.exception;

/**
 * Log messages used when organization-related exceptions are raised.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationExceptionLogConstant {

    private OrganizationExceptionLogConstant() {
    }

    // Log message when organization lookup fails.
    public static final String ORG_NOT_FOUND =
            "Organization not found: organizationId={}";

    // Log message when organization membership lookup fails.
    public static final String ORG_MEMBER_NOT_FOUND =
            "Organization member not found: organizationId={}, userId={}";

    // Log message when OWNER role assignment is attempted.
    public static final String ORG_OWNER_ROLE_NOT_ASSIGNABLE =
            "Attempted to assign OWNER role";

    // Log message when a user tries to update their own role.
    public static final String ORG_CANNOT_UPDATE_OWN_ROLE =
            "User attempted to update their own role";

    // Log message when organization is already disabled.
    public static final String ORG_ALREADY_DISABLED =
            "Organization is already disabled";

    // Log message when organization is not disabled.
    public static final String ORG_NOT_DISABLED =
            "Organization is not disabled";

    // Log message when organization member is already disabled.
    public static final String MEMBER_ALREADY_DISABLED =
            "Organization member is already disabled";

    // Log message when organization member is not disabled.
    public static final String MEMBER_NOT_DISABLED =
            "Organization member is not disabled";

    // Log message when user has insufficient permissions for lifecycle operation.
    public static final String ORG_INSUFFICIENT_PERMISSIONS =
            "User has insufficient permissions for organization lifecycle operation";

    // Log message when sole owner tries to leave organization.
    public static final String MEMBER_SOLE_OWNER =
            "User is sole owner and cannot leave organization";

    // Log message when an admin attempts to modify an owner-role member.
    public static final String ADMIN_CANNOT_MODIFY_OWNER =
            "Admin attempted to modify an owner member";
}
