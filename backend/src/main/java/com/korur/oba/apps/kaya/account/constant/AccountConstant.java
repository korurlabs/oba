package com.korur.oba.apps.kaya.account.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits and error value keys of the account domain.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AccountConstant {

    private AccountConstant() {
    }

    // --- Entity fields (specifications and sorting) ---
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_PROVIDER = "provider";
    public static final String FIELD_ACCOUNT_EMAIL = "accountEmail";
    public static final String FIELD_ACCOUNT_ORGANIZATION_UUID = "accountOrganizationUuid";
    public static final String FIELD_ACCOUNT_ORGANIZATION_NAME = "accountOrganizationName";
    public static final String FIELD_ACCOUNT = "account";
    public static final String FIELD_USER = "user";

    // --- Error value keys ---
    public static final String FIELD_ACCOUNT_ID = "accountId";
    public static final String FIELD_USER_ID = "userId";

    // --- Audit detail keys ---
    public static final String FIELD_SOURCE = "source";

    // --- Limits ---
    // A steady stream of batches rewrites the last-seen times at most once in this many seconds.
    public static final long SEEN_REFRESH_SECONDS = 60;
}
