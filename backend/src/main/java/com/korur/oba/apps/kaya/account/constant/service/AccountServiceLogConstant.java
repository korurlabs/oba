package com.korur.oba.apps.kaya.account.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the account services.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AccountServiceLogConstant {

    private AccountServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_ACCOUNTS = "Listing accounts of organization {} with {}";
    public static final String READING_ACCOUNT = "Reading account {} of organization {}";
    public static final String ACCOUNT_NOT_FOUND = "Account {} not found in organization {}";

    // --- Learning ---
    public static final String ACCOUNT_REFRESHED = "Refreshed account {} for user {}";
    public static final String ACCOUNT_CREATED = "Created {} account {} in organization {} for user {}";
    public static final String ACCOUNT_LINKED = "Linked account {} to user {} of organization {}";
    public static final String ACCOUNT_LEARNED = "Added account {} to user {} of organization {} from machine {}";
    public static final String ACCOUNT_LEARNED_CONCURRENTLY = "Refused: {} account {} of organization {} was recorded by a concurrent request";
    public static final String LINK_LEARNED_CONCURRENTLY = "Refused: account {} was linked to user {} by a concurrent request";

    // --- Unlinking ---
    public static final String LINK_NOT_FOUND = "Account {} is not linked to user {} in organization {}";
    public static final String ACCOUNT_UNLINKED = "Unlinked account {} from user {} of organization {}";
    public static final String ACCOUNT_DELETED = "Deleted account {} of organization {}: no user uses it any more";
}
