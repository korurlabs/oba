package com.korur.oba.shrine.access.constant.controller;

/**
 * Response message constants returned by the app grant controller.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AppGrantControllerResponseConstant {

    private AppGrantControllerResponseConstant() {
    }

    // Response message for list app grants endpoint.
    public static final String RESPONSE_APP_GRANTS_RETRIEVED = "App grants retrieved successfully";
    // Response message for create app grant endpoint.
    public static final String RESPONSE_APP_GRANTED = "App granted successfully";
    // Response message for revoke app grant endpoint.
    public static final String RESPONSE_APP_GRANT_REVOKED = "App grant revoked successfully";
    // Response message for list member app access endpoint.
    public static final String RESPONSE_MEMBER_APP_ACCESS_RETRIEVED = "Member app access retrieved successfully";
}
