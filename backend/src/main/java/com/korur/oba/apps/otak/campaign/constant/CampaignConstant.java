package com.korur.oba.apps.otak.campaign.constant;

/**
 * Field names and validation messages of the campaign domain. Names, descriptions, texts and
 * post addresses have no length limit, as their TEXT columns have none.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CampaignConstant {

    private CampaignConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_ID = "id";
    public static final String FIELD_CAMPAIGN_ID = "campaignId";
    public static final String FIELD_CONTENT_ID = "contentId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_OWNER_USER_ID = "ownerUserId";
    public static final String FIELD_EVENT_ID = "eventId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_START_DATE = "startDate";
    public static final String FIELD_END_DATE = "endDate";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_PLANNED_AT = "plannedAt";
    public static final String FIELD_PUBLISHED_AT = "publishedAt";
    public static final String FIELD_UPDATED_AT = "updatedAt";

    // -------------Validation messages-----------//

    public static final String NAME_REQUIRED_MESSAGE = "Name is required";
    public static final String LEGAL_ENTITY_REQUIRED_MESSAGE = "Legal entity is required";
    public static final String CHANNEL_REQUIRED_MESSAGE = "Channel is required";
    public static final String LANGUAGE_REQUIRED_MESSAGE = "Language is required";
    public static final String TEXT_REQUIRED_MESSAGE = "Text is required";
    public static final String STATUS_REQUIRED_MESSAGE = "Status is required";
    public static final String POST_URL_BLANK_MESSAGE = "Post URL can not be empty; send null when there is none";
    public static final String POST_URL_PATTERN_MESSAGE = "Post URL must be a full http or https address";
    public static final String SEARCH_BLANK_MESSAGE = "Search can not be empty; leave it out to list every campaign";
}
