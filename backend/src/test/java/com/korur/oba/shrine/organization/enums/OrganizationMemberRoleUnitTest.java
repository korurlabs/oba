package com.korur.oba.shrine.organization.enums;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK

// 4. Internal

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationMemberRoleUnitTest {

    @Test
    void isAtLeast_ordersOwnerAdminMemberGuest() {
        assertTrue(OrganizationMemberRole.OWNER.isAtLeast(OrganizationMemberRole.ADMIN));
        assertTrue(OrganizationMemberRole.ADMIN.isAtLeast(OrganizationMemberRole.ADMIN));
        assertFalse(OrganizationMemberRole.MEMBER.isAtLeast(OrganizationMemberRole.ADMIN));
        assertTrue(OrganizationMemberRole.ADMIN.isAtLeast(OrganizationMemberRole.MEMBER));
        assertTrue(OrganizationMemberRole.MEMBER.isAtLeast(OrganizationMemberRole.GUEST));
        assertFalse(OrganizationMemberRole.GUEST.isAtLeast(OrganizationMemberRole.MEMBER));
        assertFalse(OrganizationMemberRole.ADMIN.isAtLeast(OrganizationMemberRole.OWNER));
    }

    @Test
    void isAtLeast_guestSatisfiesGuestMinimum() {
        assertTrue(OrganizationMemberRole.GUEST.isAtLeast(OrganizationMemberRole.GUEST));
    }
}
