package com.korur.oba.shrine.organization.constant.service;

/**
 * Log messages used by organization services.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationServiceLogConstant {

    private OrganizationServiceLogConstant() {
    }

    // Log message after organization creation.
    public static final String ORG_CREATED =
            "Organization created";

    // Log message after organization update.
    public static final String ORG_UPDATED =
            "Organization updated";

    // Log message after organization is disabled.
    public static final String ORG_DISABLED =
            "Organization disabled";

    // Log message after organization is enabled.
    public static final String ORG_ENABLED =
            "Organization enabled";

    // Log message after organization is soft-deleted.
    public static final String ORG_SOFT_DELETED =
            "Organization soft-deleted";

    // Audit constants
    public static final String AUDIT_ENTITY_TYPE = "Organization";
    public static final String AUDIT_ACTION_ORG_DISABLED = "ORG_DISABLED";
    public static final String AUDIT_ACTION_ORG_ENABLED = "ORG_ENABLED";
    public static final String AUDIT_ACTION_ORG_DELETED = "ORG_DELETED";

}
