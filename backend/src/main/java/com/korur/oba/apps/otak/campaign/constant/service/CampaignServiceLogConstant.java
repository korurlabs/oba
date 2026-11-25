package com.korur.oba.apps.otak.campaign.constant.service;

/**
 * Log message constants for campaign service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CampaignServiceLogConstant {

    private CampaignServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CAMPAIGN_CREATED = "Campaign created: id={}, organizationId={}, createdBy={}";
    public static final String CAMPAIGN_UPDATED = "Campaign updated: id={}, organizationId={}";
    public static final String CAMPAIGN_DELETED = "Campaign deleted: id={}, organizationId={}";
    public static final String LISTING_CAMPAIGNS = "Listing campaigns: organizationId={}, request={}";
    public static final String READING_CAMPAIGN = "Reading campaign: id={}, organizationId={}";
}
