package com.korur.oba.shrine.organization.constant.controller;

/**
 * Log messages used by the organization member controller.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationMemberControllerLogConstant {

    private OrganizationMemberControllerLogConstant() {
    }

    // Log message when a list members request is received.
    public static final String ORG_MEMBERS_LIST_REQUEST =
            "Received request to list organization members";

    // Log message when a list members request completes.
    public static final String ORG_MEMBERS_LIST_REQUEST_COMPLETED =
            "Organization members list request completed";

    // Log message when a remove member request is received.
    public static final String ORG_MEMBER_REMOVE_REQUEST =
            "Received request to remove a member from organization";

    // Log message when a remove member request completes.
    public static final String ORG_MEMBER_REMOVE_REQUEST_COMPLETED =
            "Organization member removal request completed";

    // Log message when a change member role request is received.
    public static final String ORG_MEMBER_ROLE_CHANGE_REQUEST =
            "Received request to change organization member role";

    // Log message when a change member role request completes.
    public static final String ORG_MEMBER_ROLE_CHANGE_REQUEST_COMPLETED =
            "Organization member role change request completed";

    // Log message when a restore member request is received.
    public static final String ORG_MEMBER_RESTORE_REQUEST =
            "Received request to restore a member in organization";

    // Log message when a restore member request completes.
    public static final String ORG_MEMBER_RESTORE_REQUEST_COMPLETED =
            "Organization member restore request completed";

    // Log message when a member status update request is received.
    public static final String ORG_MEMBER_STATUS_UPDATE_REQUEST =
            "Received request to update member status in organization";

    // Log message when a member status update request completes.
    public static final String ORG_MEMBER_STATUS_UPDATE_REQUEST_COMPLETED =
            "Organization member status update request completed";

    // Log message when a leave organization request is received.
    public static final String ORG_MEMBER_LEAVE_REQUEST =
            "Received request to leave organization";

    // Log message when a leave organization request completes.
    public static final String ORG_MEMBER_LEAVE_REQUEST_COMPLETED =
            "Leave organization request completed";
}
