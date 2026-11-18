package com.korur.oba.shrine.organization.constant.controller;

/**
 * Response message constants returned by the organization member controller.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationMemberControllerResponseConstant {

    private OrganizationMemberControllerResponseConstant() {
    }

    // Response message for list organization members endpoint.
    public static final String RESPONSE_ORGANIZATION_MEMBERS_RETRIEVED = "Organization members retrieved successfully";
    // Response message for remove member endpoint.
    public static final String RESPONSE_ORGANIZATION_MEMBER_REMOVED = "Organization member removed successfully";
    // Response message for change member role endpoint.
    public static final String RESPONSE_ORGANIZATION_MEMBER_ROLE_CHANGED = "Organization member role changed successfully";
    // Response message for restore member endpoint.
    public static final String RESPONSE_ORGANIZATION_MEMBER_RESTORED = "Organization member restored successfully";
    // Response message for update member status endpoint.
    public static final String RESPONSE_ORGANIZATION_MEMBER_STATUS_UPDATED = "Organization member status updated successfully";
    // Response message for leave organization endpoint.
    public static final String RESPONSE_ORGANIZATION_LEFT = "Left organization successfully";
}
