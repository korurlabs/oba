package com.korur.oba.apps.kaya.delivery.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Response messages of the delivery controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DeliveryControllerResponseConstant {

    private DeliveryControllerResponseConstant() {
    }

    public static final String RESPONSE_REPOSITORIES_RETRIEVED = "Repository list retrieved";
    public static final String RESPONSE_CURRENT_RETRIEVED = "Current release retrieved";
    public static final String RESPONSE_RELEASE_RETRIEVED = "Signed release retrieved";
    public static final String RESPONSE_PROPOSAL_CREATED = "Proposal created and submitted for review";
}
