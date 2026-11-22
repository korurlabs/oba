package com.korur.oba.shrine.legalentity.constant;

/**
 * Field names, limits and validation messages of the legal entity domain. The limits repeat
 * the column sizes of shrine.legal_entities, so a request that passes validation always fits
 * the table.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LegalEntityConstant {

    private LegalEntityConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_ID = "id";
    public static final String FIELD_LEGAL_ENTITY_ID = "legalEntityId";
    public static final String FIELD_SHORT_NAME = "shortName";

    // -------------Limits-----------//

    public static final int SHORT_NAME_MAX_LENGTH = 100;
    public static final int LEGAL_NAME_MAX_LENGTH = 200;
    public static final int COUNTRY_MAX_LENGTH = 3;
    public static final int REGISTRATION_NUMBER_MAX_LENGTH = 50;
    public static final int VAT_NUMBER_MAX_LENGTH = 50;
    public static final int STREET_MAX_LENGTH = 200;
    public static final int POSTAL_CODE_MAX_LENGTH = 20;
    public static final int CITY_MAX_LENGTH = 100;
    public static final int GOVERNING_LAW_MAX_LENGTH = 200;
    public static final int COURT_MAX_LENGTH = 200;

    // -------------Validation messages-----------//

    public static final String SHORT_NAME_REQUIRED_MESSAGE = "Short name is required";
    public static final String SHORT_NAME_SIZE_MESSAGE = "Short name can not be longer than 100 characters";
    public static final String LEGAL_NAME_REQUIRED_MESSAGE = "Legal name is required";
    public static final String LEGAL_NAME_SIZE_MESSAGE = "Legal name can not be longer than 200 characters";
    public static final String COUNTRY_REQUIRED_MESSAGE = "Country code is required";
    public static final String COUNTRY_SIZE_MESSAGE = "Country code must be at most 3 characters";
    public static final String REGISTRATION_NUMBER_BLANK_MESSAGE = "Registration number must be null or not blank";
    public static final String REGISTRATION_NUMBER_SIZE_MESSAGE =
            "Registration number can not be longer than 50 characters";
    public static final String VAT_NUMBER_BLANK_MESSAGE = "VAT number must be null or not blank";
    public static final String VAT_NUMBER_SIZE_MESSAGE = "VAT number can not be longer than 50 characters";
    public static final String STREET_BLANK_MESSAGE = "Street must be null or not blank";
    public static final String STREET_SIZE_MESSAGE = "Street can not be longer than 200 characters";
    public static final String POSTAL_CODE_BLANK_MESSAGE = "Postal code must be null or not blank";
    public static final String POSTAL_CODE_SIZE_MESSAGE = "Postal code can not be longer than 20 characters";
    public static final String CITY_BLANK_MESSAGE = "City must be null or not blank";
    public static final String CITY_SIZE_MESSAGE = "City can not be longer than 100 characters";
    public static final String GOVERNING_LAW_BLANK_MESSAGE = "Governing law must be null or not blank";
    public static final String GOVERNING_LAW_SIZE_MESSAGE = "Governing law can not be longer than 200 characters";
    public static final String COURT_BLANK_MESSAGE = "Court must be null or not blank";
    public static final String COURT_SIZE_MESSAGE = "Court can not be longer than 200 characters";
}
