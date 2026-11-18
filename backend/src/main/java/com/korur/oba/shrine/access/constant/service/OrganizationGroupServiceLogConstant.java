package com.korur.oba.shrine.access.constant.service;

/**
 * Log messages and audit values used by the organization group services.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationGroupServiceLogConstant {

    private OrganizationGroupServiceLogConstant() {
    }

    // Log message when a custom group is created.
    public static final String GROUP_CREATED =
            "Organization group created: organizationId={}, groupId={}";

    // Log message when a group is renamed or its description changes.
    public static final String GROUP_UPDATED =
            "Organization group updated: organizationId={}, groupId={}";

    // Log message when a group, its member rows and its grants are deleted.
    public static final String GROUP_DELETED =
            "Organization group deleted: organizationId={}, groupId={}, memberRows={}, grants={}";

    // Log message when members are added to a group.
    public static final String GROUP_MEMBERS_ADDED =
            "Organization group members added: organizationId={}, groupId={}, added={}";

    // Log message when a member is removed from a group.
    public static final String GROUP_MEMBER_REMOVED =
            "Organization group member removed: organizationId={}, groupId={}, userId={}";

    // Log message when a new organization gets its Everyone group with a grant of every app.
    public static final String EVERYONE_GROUP_CREATED =
            "Everyone group created: organizationId={}, groupId={}, grants={}";

    // Audit constants
    public static final String AUDIT_ENTITY_TYPE = "OrganizationGroup";
    public static final String AUDIT_ACTION_GROUP_CREATED = "GROUP_CREATED";
    public static final String AUDIT_ACTION_GROUP_UPDATED = "GROUP_UPDATED";
    public static final String AUDIT_ACTION_GROUP_DELETED = "GROUP_DELETED";
    public static final String AUDIT_ACTION_GROUP_MEMBERS_ADDED = "GROUP_MEMBERS_ADDED";
    public static final String AUDIT_ACTION_GROUP_MEMBER_REMOVED = "GROUP_MEMBER_REMOVED";

    // Keys of the old and new values of an audit entry.
    public static final String AUDIT_FIELD_NAME = "name";
    public static final String AUDIT_FIELD_DESCRIPTION = "description";
    public static final String AUDIT_FIELD_USER_ID = "userId";
    public static final String AUDIT_FIELD_USER_IDS = "userIds";
    public static final String AUDIT_FIELD_MEMBER_COUNT = "memberCount";
    public static final String AUDIT_FIELD_APPS = "apps";
    public static final String AUDIT_FIELD_SOFT_DELETED = "softDeleted";
}
