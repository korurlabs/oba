package com.korur.oba.apps.otak.icp.constant.service;

/**
 * Log message constants for ICP service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class IcpServiceLogConstant {

    private IcpServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String ICP_CREATED = "ICP created: id={}, organizationId={}, createdBy={}";
    public static final String ICP_UPDATED = "ICP updated: id={}, organizationId={}";
    public static final String ICP_DELETED = "ICP deleted: id={}, organizationId={}";
    public static final String LISTING_ICPS = "Listing ICPs: organizationId={}, request={}";
    public static final String READING_ICP = "Reading ICP: id={}, organizationId={}";
}
