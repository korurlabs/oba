package com.korur.oba.apps.kaya.account.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the account controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AccountControllerLogConstant {

    private AccountControllerLogConstant() {
    }

    public static final String ACCOUNT_LIST_REQUEST = "Received request to list the accounts of organization {}";
    public static final String ACCOUNT_LIST_COMPLETED = "Listed {} accounts of organization {}";
    public static final String ACCOUNT_GET_REQUEST = "Received request to read account {} of organization {}";
    public static final String ACCOUNT_GET_COMPLETED = "Read account {} of organization {}";
}
