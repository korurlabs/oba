package com.korur.oba.shrine.access.constant.controller;

/**
 * Response message constants returned by the organization group controller.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationGroupControllerResponseConstant {

    private OrganizationGroupControllerResponseConstant() {
    }

    // Response message for list groups endpoint.
    public static final String RESPONSE_GROUPS_RETRIEVED = "Organization groups retrieved successfully";
    // Response message for create group endpoint.
    public static final String RESPONSE_GROUP_CREATED = "Organization group created successfully";
    // Response message for update group endpoint.
    public static final String RESPONSE_GROUP_UPDATED = "Organization group updated successfully";
    // Response message for delete group endpoint.
    public static final String RESPONSE_GROUP_DELETED = "Organization group deleted successfully";
    // Response message for list group members endpoint.
    public static final String RESPONSE_GROUP_MEMBERS_RETRIEVED = "Organization group members retrieved successfully";
    // Response message for add group members endpoint.
    public static final String RESPONSE_GROUP_MEMBERS_ADDED = "Organization group members added successfully";
    // Response message for remove group member endpoint.
    public static final String RESPONSE_GROUP_MEMBER_REMOVED = "Organization group member removed successfully";
}
