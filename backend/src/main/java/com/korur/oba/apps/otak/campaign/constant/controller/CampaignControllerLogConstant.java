package com.korur.oba.apps.otak.campaign.constant.controller;

/**
 * Log message constants for campaign controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CampaignControllerLogConstant {

    private CampaignControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CAMPAIGN_LIST_REQUEST = "Received request to list campaigns";
    public static final String CAMPAIGN_LIST_COMPLETED = "List campaigns request completed: count={}";

    public static final String CAMPAIGN_CREATE_REQUEST = "Received request to create campaign";
    public static final String CAMPAIGN_CREATE_COMPLETED = "Create campaign request completed";

    public static final String CAMPAIGN_GET_REQUEST = "Received request to get campaign";
    public static final String CAMPAIGN_GET_COMPLETED = "Get campaign request completed";

    public static final String CAMPAIGN_UPDATE_REQUEST = "Received request to update campaign";
    public static final String CAMPAIGN_UPDATE_COMPLETED = "Update campaign request completed";

    public static final String CAMPAIGN_DELETE_REQUEST = "Received request to delete campaign";
    public static final String CAMPAIGN_DELETE_COMPLETED = "Delete campaign request completed";
}
