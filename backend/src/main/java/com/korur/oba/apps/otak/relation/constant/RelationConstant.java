package com.korur.oba.apps.otak.relation.constant;

/**
 * Field names, limits and validation messages of the relation domain. The limits
 * repeat the column sizes of otak.relations, so a request that passes validation
 * always fits the table.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RelationConstant {

    private RelationConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_ID = "id";
    public static final String FIELD_RELATION_ID = "relationId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_OWNER_USER_ID = "ownerUserId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_STAGE = "stage";
    public static final String FIELD_UPDATED_AT = "updatedAt";
    public static final String FIELD_EVENT_ID = "eventId";
    public static final String FIELD_CAMPAIGN_ID = "campaignId";

    // -------------Limits-----------//

    public static final int NAME_MAX_LENGTH = 200;
    public static final int WEBSITE_MAX_LENGTH = 500;
    public static final int SECTOR_MAX_LENGTH = 100;
    public static final int REGISTRATION_NUMBER_MAX_LENGTH = 50;
    public static final int VAT_NUMBER_MAX_LENGTH = 50;
    public static final int STREET_MAX_LENGTH = 200;
    public static final int POSTAL_CODE_MAX_LENGTH = 20;
    public static final int CITY_MAX_LENGTH = 100;
    public static final int NOTES_MAX_LENGTH = 10000;
    public static final int SEARCH_MAX_LENGTH = 200;

    /**
     * Whole-euro digits of the expected value; with two decimals it fits NUMERIC(12,2).
     */
    public static final int EXPECTED_VALUE_INTEGER_DIGITS = 10;
    public static final int EXPECTED_VALUE_FRACTION_DIGITS = 2;
    public static final String EXPECTED_VALUE_MIN = "0.00";

    // -------------Patterns-----------//

    /**
     * An absolute http or https URL without spaces.
     */
    public static final String WEBSITE_PATTERN = "^https?://[^\\s/?#]+\\.[^\\s]+$";

    // -------------Search-----------//

    /**
     * Wraps a search keyword so the LIKE filter matches it anywhere in the name.
     */
    public static final String LIKE_WILDCARD = "%";

    // -------------Validation messages-----------//

    public static final String NAME_REQUIRED_MESSAGE = "Name is required";
    public static final String NAME_SIZE_MESSAGE = "Name can not be longer than 200 characters";
    public static final String TYPE_REQUIRED_MESSAGE = "Type is required";
    public static final String STAGE_REQUIRED_MESSAGE = "Stage is required";
    public static final String WEBSITE_BLANK_MESSAGE = "Website can not be empty; send null for none";
    public static final String WEBSITE_SIZE_MESSAGE = "Website can not be longer than 500 characters";
    public static final String WEBSITE_PATTERN_MESSAGE = "Website must be a full http or https address";
    public static final String SECTOR_BLANK_MESSAGE = "Sector can not be empty; send null for none";
    public static final String SECTOR_SIZE_MESSAGE = "Sector can not be longer than 100 characters";
    public static final String REGISTRATION_NUMBER_BLANK_MESSAGE =
            "Registration number can not be empty; send null for none";
    public static final String REGISTRATION_NUMBER_SIZE_MESSAGE = "Registration number can not be longer than 50 characters";
    public static final String VAT_NUMBER_BLANK_MESSAGE = "VAT number can not be empty; send null for none";
    public static final String VAT_NUMBER_SIZE_MESSAGE = "VAT number can not be longer than 50 characters";
    public static final String STREET_BLANK_MESSAGE = "Street can not be empty; send null for none";
    public static final String STREET_SIZE_MESSAGE = "Street can not be longer than 200 characters";
    public static final String POSTAL_CODE_BLANK_MESSAGE = "Postal code can not be empty; send null for none";
    public static final String POSTAL_CODE_SIZE_MESSAGE = "Postal code can not be longer than 20 characters";
    public static final String CITY_BLANK_MESSAGE = "City can not be empty; send null for none";
    public static final String CITY_SIZE_MESSAGE = "City can not be longer than 100 characters";
    public static final String EXPECTED_VALUE_MIN_MESSAGE = "Expected value can not be negative";
    public static final String EXPECTED_VALUE_DIGITS_MESSAGE =
            "Expected value can have at most 10 digits before and 2 after the decimal point";
    public static final String NOTES_BLANK_MESSAGE = "Notes can not be empty; send null for none";
    public static final String NOTES_SIZE_MESSAGE = "Notes can not be longer than 10000 characters";
    public static final String SEARCH_BLANK_MESSAGE = "Search can not be empty; leave it out for none";
    public static final String SEARCH_SIZE_MESSAGE = "Search can not be longer than 200 characters";
}
