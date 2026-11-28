package com.korur.oba.apps.otak.campaign.constant.controller;

/**
 * Log message constants for campaign content controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CampaignContentControllerLogConstant {

    private CampaignContentControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CAMPAIGN_CONTENT_LIST_REQUEST = "Received request to list campaign contents";
    public static final String CAMPAIGN_CONTENT_LIST_COMPLETED = "List campaign contents request completed: count={}";

    public static final String CAMPAIGN_CONTENT_CREATE_REQUEST = "Received request to create campaign content";
    public static final String CAMPAIGN_CONTENT_CREATE_COMPLETED = "Create campaign content request completed";

    public static final String CAMPAIGN_CONTENT_UPDATE_REQUEST = "Received request to update campaign content";
    public static final String CAMPAIGN_CONTENT_UPDATE_COMPLETED = "Update campaign content request completed";

    public static final String CAMPAIGN_CONTENT_DELETE_REQUEST = "Received request to delete campaign content";
    public static final String CAMPAIGN_CONTENT_DELETE_COMPLETED = "Delete campaign content request completed";
}
