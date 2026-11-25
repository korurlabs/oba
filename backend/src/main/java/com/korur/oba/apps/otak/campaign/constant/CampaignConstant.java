package com.korur.oba.apps.otak.campaign.constant;

/**
 * Field names, limits and validation messages of the campaign domain. The limits repeat the
 * column sizes and the text check constraint of otak.campaigns and otak.campaign_contents,
 * so a request that passes validation always fits the tables.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class CampaignConstant {

    private CampaignConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_ID = "id";
    public static final String FIELD_CAMPAIGN_ID = "campaignId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_OWNER_USER_ID = "ownerUserId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_CHANNEL = "channel";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_PLANNED_AT = "plannedAt";
    public static final String FIELD_PUBLISHED_AT = "publishedAt";
    public static final String FIELD_UPDATED_AT = "updatedAt";
    public static final String FIELD_LANGUAGE = "language";

    // -------------Limits-----------//

    public static final int NAME_MAX_LENGTH = 200;
    public static final int POST_URL_MAX_LENGTH = 500;
    public static final int SEARCH_MAX_LENGTH = 200;

    // -------------Validation messages-----------//

    public static final String NAME_REQUIRED_MESSAGE = "Name is required";
    public static final String NAME_SIZE_MESSAGE = "Name can not be longer than 200 characters";
    public static final String CHANNEL_REQUIRED_MESSAGE = "Channel is required";
    public static final String STATUS_REQUIRED_MESSAGE = "Status is required";
    public static final String POST_URL_BLANK_MESSAGE = "Post URL can not be empty; send null when there is none";
    public static final String POST_URL_SIZE_MESSAGE = "Post URL can not be longer than 500 characters";
    public static final String POST_URL_PATTERN_MESSAGE = "Post URL must be a full http or https address";
    public static final String CONTENT_REQUIRED_MESSAGE = "A text entry can not be null";
    public static final String CONTENT_LANGUAGE_REQUIRED_MESSAGE = "Language is required";
    public static final String CONTENT_TEXT_REQUIRED_MESSAGE = "Text is required";
    public static final String SEARCH_BLANK_MESSAGE = "Search can not be empty; leave it out to list every campaign";
    public static final String SEARCH_SIZE_MESSAGE = "Search can not be longer than 200 characters";
}
