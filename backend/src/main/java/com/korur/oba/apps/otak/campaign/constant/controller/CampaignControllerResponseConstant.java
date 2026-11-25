package com.korur.oba.apps.otak.campaign.constant.controller;

/**
 * Response message constants for campaign controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CampaignControllerResponseConstant {

    private CampaignControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_CAMPAIGN_LIST_RETRIEVED = "Campaigns retrieved successfully";
    public static final String RESPONSE_CAMPAIGN_CREATED = "Campaign created successfully";
    public static final String RESPONSE_CAMPAIGN_RETRIEVED = "Campaign retrieved successfully";
    public static final String RESPONSE_CAMPAIGN_UPDATED = "Campaign updated successfully";
    public static final String RESPONSE_CAMPAIGN_DELETED = "Campaign deleted successfully";
}
