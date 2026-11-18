package com.korur.oba.apps.kaya.delivery.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the delivery controller. A remote URL is never logged raw, since it may carry
 * an access token; the service logs the normalised form.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DeliveryControllerLogConstant {

    private DeliveryControllerLogConstant() {
    }

    public static final String CURRENT_REQUEST = "Request for the current release from machine {}";
    public static final String CURRENT_NOT_MODIFIED = "Current release unchanged for machine {}, ETag {}";
    public static final String CURRENT_COMPLETED = "Served current release {} to machine {}, ETag {}";
    public static final String RELEASE_REQUEST = "Request for release {} from machine {}";
    public static final String RELEASE_BY_NUMBER_REQUEST = "Request for release number {} from machine {}";
    public static final String RELEASE_COMPLETED = "Served release {} ({}) to machine {}";
    public static final String BLOB_REQUEST = "Request for blob {} from machine {}";
    public static final String BLOB_COMPLETED = "Served blob {} ({} bytes) to machine {}";
    public static final String PROPOSAL_REQUEST = "Proposal of {} files on release {} from machine {}";
    public static final String PROPOSAL_COMPLETED = "Proposal {} of pack {} in review, from machine {}";
}
