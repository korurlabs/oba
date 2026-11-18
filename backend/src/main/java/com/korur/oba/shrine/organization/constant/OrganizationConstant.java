package com.korur.oba.shrine.organization.constant;

/**
 * Shared organization constants for field names and messages.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationConstant {

    private OrganizationConstant() {
    }

    // Field key used in error payloads for organization id.
    public static final String FIELD_ORGANIZATION_ID = "OrganizationId";
    // Field key used in error payloads for organization name.
    public static final String FIELD_ORGANIZATION_NAME = "OrganizationName";
    // Field key used in error payloads for organization member id.
    public static final String FIELD_ORGANIZATION_MEMBER_ID = "OrganizationMemberId";
    // Field key used in error payloads for organization user id.
    public static final String FIELD_ORGANIZATION_USER_ID = "OrganizationUserId";
    // Field key used in error payloads for organization role.
    public static final String FIELD_ORGANIZATION_ROLE = "OrganizationMemberRole";
}
