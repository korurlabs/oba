package com.korur.oba.apps.otak.campaign.constant;

/**
 * Log message constants for campaign domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CampaignExceptionLogConstant {

    private CampaignExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for a campaign that the organization does not have, missing or of another organization.
     */
    public static final String CAMPAIGN_NOT_FOUND = "Campaign not found: id={}, organizationId={}";

    /**
     * Log message for a campaign id that no campaign of any organization has.
     */
    public static final String CAMPAIGN_NOT_FOUND_BY_ID = "Campaign not found: id={}";

    /**
     * Log message for a name another campaign of the organization already has, case ignored.
     */
    public static final String CAMPAIGN_NAME_TAKEN = "Campaign name refused, already taken: organizationId={}, name={}";

    /**
     * Log message for a planned or published moment that does not fit the status.
     */
    public static final String CAMPAIGN_STATUS_DATES_INVALID =
            "Campaign moments refused for the status: status={}, plannedAt={}, publishedAt={}";

    /**
     * Log message for two texts of a campaign in one language.
     */
    public static final String CAMPAIGN_CONTENT_LANGUAGE_REPEATED = "Campaign texts refused, language repeated: language={}";

    /**
     * Log message for an owner who is not an active member of the organization.
     */
    public static final String OWNER_NOT_ORGANIZATION_MEMBER =
            "Campaign owner is not an active member: organizationId={}, ownerUserId={}";
}
