package com.korur.oba.shrine.access.constant.service;

/**
 * Log messages and audit values used by the app grant services.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AppAccessServiceLogConstant {

    private AppAccessServiceLogConstant() {
    }

    // Log message when an app is granted.
    public static final String APP_GRANTED =
            "App granted: organizationId={}, grantId={}, app={}";

    // Log message when a grant request finds the same grant already in place.
    public static final String APP_GRANT_ALREADY_EXISTS =
            "App grant already exists: organizationId={}, grantId={}, app={}";

    // Log message when an app grant is revoked.
    public static final String APP_GRANT_REVOKED =
            "App grant revoked: organizationId={}, grantId={}, app={}";

    // Log message when the group rows and direct grants of a leaving or removed member are deleted.
    public static final String MEMBER_ACCESS_REMOVED =
            "Member app access removed: organizationId={}, userId={}, groupRows={}, grants={}";

    // Log message when a user who lost an app gets an app access revoked event.
    public static final String APP_ACCESS_REVOKED =
            "App access revoked: organizationId={}, userId={}, app={}";

    // Audit constants
    public static final String AUDIT_ENTITY_TYPE = "AppGrant";
    public static final String AUDIT_ACTION_APP_GRANTED = "APP_GRANTED";
    public static final String AUDIT_ACTION_APP_REVOKED = "APP_REVOKED";
    public static final String AUDIT_ACTION_MEMBER_ACCESS_REMOVED = "MEMBER_ACCESS_REMOVED";

    // Keys of the old and new values of an audit entry.
    public static final String AUDIT_FIELD_APP = "app";
    public static final String AUDIT_FIELD_USER_ID = "userId";
    public static final String AUDIT_FIELD_GROUP_ID = "groupId";
    public static final String AUDIT_FIELD_GRANT_IDS = "grantIds";
    public static final String AUDIT_FIELD_GROUP_MEMBER_IDS = "groupMemberIds";
    public static final String AUDIT_FIELD_SOFT_DELETED = "softDeleted";
}
