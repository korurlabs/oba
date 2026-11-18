package com.korur.oba.shrine.access.constant.controller;

/**
 * Log messages used by the organization group controller.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationGroupControllerLogConstant {

    private OrganizationGroupControllerLogConstant() {
    }

    // Log message when a list groups request is received.
    public static final String GROUPS_LIST_REQUEST =
            "Received request to list organization groups: organizationId={}";

    // Log message when a list groups request completes.
    public static final String GROUPS_LIST_REQUEST_COMPLETED =
            "Organization groups list request completed: organizationId={}";

    // Log message when a create group request is received.
    public static final String GROUP_CREATE_REQUEST =
            "Received request to create an organization group: organizationId={}";

    // Log message when a create group request completes.
    public static final String GROUP_CREATE_REQUEST_COMPLETED =
            "Organization group create request completed: organizationId={}, groupId={}";

    // Log message when an update group request is received.
    public static final String GROUP_UPDATE_REQUEST =
            "Received request to update an organization group: organizationId={}, groupId={}";

    // Log message when an update group request completes.
    public static final String GROUP_UPDATE_REQUEST_COMPLETED =
            "Organization group update request completed: organizationId={}, groupId={}";

    // Log message when a delete group request is received.
    public static final String GROUP_DELETE_REQUEST =
            "Received request to delete an organization group: organizationId={}, groupId={}";

    // Log message when a delete group request completes.
    public static final String GROUP_DELETE_REQUEST_COMPLETED =
            "Organization group delete request completed: organizationId={}, groupId={}";

    // Log message when a list group members request is received.
    public static final String GROUP_MEMBERS_LIST_REQUEST =
            "Received request to list organization group members: organizationId={}, groupId={}";

    // Log message when a list group members request completes.
    public static final String GROUP_MEMBERS_LIST_REQUEST_COMPLETED =
            "Organization group members list request completed: organizationId={}, groupId={}";

    // Log message when an add group members request is received.
    public static final String GROUP_MEMBERS_ADD_REQUEST =
            "Received request to add organization group members: organizationId={}, groupId={}";

    // Log message when an add group members request completes.
    public static final String GROUP_MEMBERS_ADD_REQUEST_COMPLETED =
            "Organization group members add request completed: organizationId={}, groupId={}";

    // Log message when a remove group member request is received.
    public static final String GROUP_MEMBER_REMOVE_REQUEST =
            "Received request to remove an organization group member: organizationId={}, groupId={}, userId={}";

    // Log message when a remove group member request completes.
    public static final String GROUP_MEMBER_REMOVE_REQUEST_COMPLETED =
            "Organization group member remove request completed: organizationId={}, groupId={}, userId={}";
}
