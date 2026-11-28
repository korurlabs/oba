package com.korur.oba.apps.otak.campaign.constant.service;

/**
 * Log message constants for campaign content service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CampaignContentServiceLogConstant {

    private CampaignContentServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CAMPAIGN_CONTENT_CREATED = "Campaign content created: id={}, campaignId={}, organizationId={}";
    public static final String CAMPAIGN_CONTENT_UPDATED = "Campaign content updated: id={}, campaignId={}, organizationId={}";
    public static final String CAMPAIGN_CONTENT_DELETED = "Campaign content deleted: id={}, campaignId={}, organizationId={}";
    public static final String LISTING_CAMPAIGN_CONTENTS = "Listing campaign contents: campaignId={}, organizationId={}";
}
