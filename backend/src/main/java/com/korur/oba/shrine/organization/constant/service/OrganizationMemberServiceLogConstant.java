package com.korur.oba.shrine.organization.constant.service;

/**
 * Log messages used by organization member services.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationMemberServiceLogConstant {

    private OrganizationMemberServiceLogConstant() {
    }

    public static final String ORG_MEMBER_REMOVED =
            "Organization member soft-deleted";

    public static final String ORG_MEMBER_RESTORED =
            "Organization member restored from soft-delete";

    public static final String ORG_MEMBER_DISABLED =
            "Organization member disabled";

    public static final String ORG_MEMBER_ENABLED =
            "Organization member enabled";

    public static final String ORG_MEMBER_LEFT =
            "User left organization";

    public static final String ORG_MEMBER_ROLE_CHANGED =
            "Organization member role changed";

    // Audit constants
    public static final String AUDIT_ENTITY_TYPE = "OrganizationMember";
    public static final String AUDIT_ACTION_MEMBER_REMOVED = "MEMBER_REMOVED";
    public static final String AUDIT_ACTION_MEMBER_RESTORED = "MEMBER_RESTORED";
    public static final String AUDIT_ACTION_MEMBER_DISABLED = "MEMBER_DISABLED";
    public static final String AUDIT_ACTION_MEMBER_ENABLED = "MEMBER_ENABLED";
    public static final String AUDIT_ACTION_MEMBER_LEFT = "MEMBER_LEFT";
}
