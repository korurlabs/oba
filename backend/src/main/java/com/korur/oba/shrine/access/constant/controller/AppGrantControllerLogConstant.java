package com.korur.oba.shrine.access.constant.controller;

/**
 * Log messages used by the app grant controller.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AppGrantControllerLogConstant {

    private AppGrantControllerLogConstant() {
    }

    // Log message when a list app grants request is received.
    public static final String APP_GRANTS_LIST_REQUEST =
            "Received request to list app grants: organizationId={}";

    // Log message when a list app grants request completes.
    public static final String APP_GRANTS_LIST_REQUEST_COMPLETED =
            "App grants list request completed: organizationId={}";

    // Log message when a create app grant request is received.
    public static final String APP_GRANT_CREATE_REQUEST =
            "Received request to grant an app: organizationId={}, app={}";

    // Log message when a create app grant request completes.
    public static final String APP_GRANT_CREATE_REQUEST_COMPLETED =
            "App grant request completed: organizationId={}, grantId={}";

    // Log message when a revoke app grant request is received.
    public static final String APP_GRANT_REVOKE_REQUEST =
            "Received request to revoke an app grant: organizationId={}, grantId={}";

    // Log message when a revoke app grant request completes.
    public static final String APP_GRANT_REVOKE_REQUEST_COMPLETED =
            "App grant revoke request completed: organizationId={}, grantId={}";

    // Log message when a list member app access request is received.
    public static final String MEMBER_APP_ACCESS_LIST_REQUEST =
            "Received request to list member app access: organizationId={}";

    // Log message when a list member app access request completes.
    public static final String MEMBER_APP_ACCESS_LIST_REQUEST_COMPLETED =
            "Member app access list request completed: organizationId={}";
}
