package com.korur.oba.shrine.identity.constant;

/**
 * Shared identity constants for user domain fields and messages.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class UserConstant {

    private UserConstant() {

    }

    public static final String FIELD_USER_DATABASE_ID = "UserDatabaseID";
    public static final String FIELD_USER_EMAIL = "Email";
    public static final String FIELD_USER_KEYCLOAK_ID = "KeycloakId";
    public static final String FIELD_USER_STATUS = "status";
    public static final String FIELD_HEADER = "header";

    public static final String REASON_EMAIL_ALREADY_EXISTS = "email_already_exists";

    public static final String FULL_NAME_SEPARATOR = " ";
}
