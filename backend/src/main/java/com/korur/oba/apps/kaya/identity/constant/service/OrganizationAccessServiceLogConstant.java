package com.korur.oba.apps.kaya.identity.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the organization access check.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationAccessServiceLogConstant {

    private OrganizationAccessServiceLogConstant() {
    }

    public static final String CHECKING_TENANT_ACCESS = "Checking access of user {} to organization {}";
    public static final String USER_NOT_ACTIVE = "Access refused: user {} has status {}";
    public static final String NOT_A_MEMBER = "Access refused: user {} is not a member of organization {}";
    public static final String MEMBERSHIP_NOT_ACTIVE = "Access refused: membership of user {} in organization {} has status {}";
    public static final String ORGANIZATION_NOT_ACTIVE = "Access refused: organization {} has status {}";
    public static final String GUEST_REFUSED = "Access refused: user {} is a GUEST of organization {}, which gets no Kaya access";
}
