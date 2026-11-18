package com.korur.oba.shrine.access.constant.exception;

/**
 * Log messages used when app access exceptions are raised.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AccessExceptionLogConstant {

    private AccessExceptionLogConstant() {
    }

    // Log message when a member is refused an app.
    public static final String APP_ACCESS_DENIED =
            "App access denied: organizationId={}, userId={}, app={}";

    // Log message when an entity addressed by type and id belongs to another organization than the request.
    public static final String RESOURCE_ORGANIZATION_MISMATCH =
            "Resource of another organization refused: userId={}, type={}, resourceId={}, "
                    + "resourceOrganizationId={}, requestOrganizationId={}";

    // Startup failure when a resource type would let a role below its app's minimum role write; a format string.
    public static final String RESOURCE_WRITE_ROLE_BELOW_APP_MINIMUM =
            "Write role %s is below the minimum role of app %s";

    // Log message when a write would attach users who may not open the app to its data.
    public static final String MEMBER_WITHOUT_APP_ACCESS =
            "Users without app access refused: organizationId={}, app={}, userIds={}";

    // Log message when a grant request names no target or two targets.
    public static final String GRANT_TARGET_INVALID =
            "App grant refused: exactly one of userId and groupId is required, organizationId={}";

    // Log message when a change to the Everyone group is refused.
    public static final String SYSTEM_GROUP_IMMUTABLE =
            "Change to the Everyone group refused: organizationId={}, groupId={}";

    // Log message when a group name is already used in the organization.
    public static final String GROUP_NAME_TAKEN =
            "Group name already used: organizationId={}, name={}";

    // Log message when a group lookup fails.
    public static final String GROUP_NOT_FOUND =
            "Group not found: organizationId={}, groupId={}";

    // Log message when an app grant lookup fails.
    public static final String GRANT_NOT_FOUND =
            "App grant not found: organizationId={}, grantId={}";

    // Log message when a user is not in the group.
    public static final String GROUP_MEMBER_NOT_FOUND =
            "Group member not found: organizationId={}, groupId={}, userId={}";
}
