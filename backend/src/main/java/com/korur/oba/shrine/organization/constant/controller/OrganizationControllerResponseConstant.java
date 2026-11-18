package com.korur.oba.shrine.organization.constant.controller;

/**
 * Response message constants returned by organization controllers.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationControllerResponseConstant {

    private OrganizationControllerResponseConstant() {
    }

    // Response message for create organization endpoint.
    public static final String RESPONSE_ORGANIZATION_CREATED = "Organization created successfully";
    // Response message for get organization endpoint.
    public static final String RESPONSE_ORGANIZATION_RETRIEVED = "Organization retrieved successfully";
    // Response message for update organization endpoint.
    public static final String RESPONSE_ORGANIZATION_UPDATED = "Organization updated successfully";
    // Response message for update organization status endpoint.
    public static final String RESPONSE_ORGANIZATION_STATUS_UPDATED = "Organization status updated successfully";
    // Response message for delete organization endpoint.
    public static final String RESPONSE_ORGANIZATION_DELETED = "Organization deleted successfully";
    // Response message for list organizations endpoint.
    public static final String RESPONSE_ORGANIZATIONS_RETRIEVED = "Organizations retrieved successfully";
}
