package com.korur.oba.apps.otak.icp.constant;

/**
 * Field names, limits and validation messages of the ICP domain. The limits repeat the
 * column size and check constraint of otak.icps, so a request that passes validation
 * always fits the table.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class IcpConstant {

    private IcpConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_ID = "id";
    public static final String FIELD_ICP_ID = "icpId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_COMPANY_SIZE_MIN = "companySizeMin";
    public static final String FIELD_COMPANY_SIZE_MAX = "companySizeMax";

    // -------------Limits-----------//

    public static final int NAME_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 10000;
    public static final int SEARCH_MAX_LENGTH = 100;

    // -------------Validation messages-----------//

    public static final String NAME_REQUIRED_MESSAGE = "Name is required";
    public static final String NAME_SIZE_MESSAGE = "Name can not be longer than 100 characters";
    public static final String INDUSTRY_REQUIRED_MESSAGE = "Industry is required";
    public static final String DESCRIPTION_BLANK_MESSAGE = "Description can not be empty; send null for none";
    public static final String DESCRIPTION_SIZE_MESSAGE = "Description can not be longer than 10000 characters";
    public static final String SEARCH_BLANK_MESSAGE = "Search can not be empty; leave it out to list every ICP";
    public static final String SEARCH_SIZE_MESSAGE = "Search can not be longer than 100 characters";
}
