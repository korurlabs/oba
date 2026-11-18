package com.korur.oba.shrine.organization.constant.controller;

/**
 * Log messages used by organization controllers.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationControllerLogConstant {

    private OrganizationControllerLogConstant() {
    }

    // Log message when a create organization request is received.
    public static final String ORG_CREATE_REQUEST =
            "Received request to create an organization";

    // Log message when a create organization request completes.
    public static final String ORG_CREATE_REQUEST_COMPLETED =
            "Organization creation request completed";

    // Log message when a list organizations request is received.
    public static final String ORG_LIST_REQUEST =
            "Received request to list organizations";

    // Log message when a list organizations request completes.
    public static final String ORG_LIST_REQUEST_COMPLETED =
            "Organization list request completed";

    // Log message when a get organization details request is received.
    public static final String ORG_DETAIL_REQUEST =
            "Received request to retrieve organization details";

    // Log message when a get organization details request completes.
    public static final String ORG_DETAIL_REQUEST_COMPLETED =
            "Organization details request completed";

    // Log message when an update organization request is received.
    public static final String ORG_UPDATE_REQUEST =
            "Received request to update an organization";

    // Log message when an update organization request completes.
    public static final String ORG_UPDATE_REQUEST_COMPLETED =
            "Organization update request completed";

    // Log message when an update organization status request is received.
    public static final String ORG_STATUS_UPDATE_REQUEST =
            "Received request to update organization status";

    // Log message when an update organization status request completes.
    public static final String ORG_STATUS_UPDATE_REQUEST_COMPLETED =
            "Organization status update request completed";

    // Log message when a delete organization request is received.
    public static final String ORG_DELETE_REQUEST =
            "Received request to delete an organization";

    // Log message when a delete organization request completes.
    public static final String ORG_DELETE_REQUEST_COMPLETED =
            "Organization deletion request completed";
}
