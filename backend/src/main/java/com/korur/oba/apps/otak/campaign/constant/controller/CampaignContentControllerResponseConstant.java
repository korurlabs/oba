package com.korur.oba.apps.otak.campaign.constant.controller;

/**
 * Response message constants for campaign content controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CampaignContentControllerResponseConstant {

    private CampaignContentControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_CAMPAIGN_CONTENT_LIST_RETRIEVED = "Campaign contents retrieved successfully";
    public static final String RESPONSE_CAMPAIGN_CONTENT_CREATED = "Campaign content created successfully";
    public static final String RESPONSE_CAMPAIGN_CONTENT_UPDATED = "Campaign content updated successfully";
    public static final String RESPONSE_CAMPAIGN_CONTENT_DELETED = "Campaign content deleted successfully";
}
