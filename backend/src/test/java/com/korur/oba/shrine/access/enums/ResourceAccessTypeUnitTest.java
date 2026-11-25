package com.korur.oba.shrine.access.enums;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceAccessTypeUnitTest {

    @Test
    void minimumRole_read_isTheMinimumRoleOfTheApp() {
        for (ResourceAccessType type : ResourceAccessType.values()) {
            assertEquals(type.getApp().getMinimumRole(), type.minimumRole(ResourceAccessAction.READ),
                    "READ of " + type);
        }
    }

    @Test
    void minimumRole_writeAndDelete_isTheWriteRole() {
        for (ResourceAccessType type : ResourceAccessType.values()) {
            OrganizationMemberRole writeRole = type == ResourceAccessType.MAIL
                    ? OrganizationMemberRole.ADMIN
                    : OrganizationMemberRole.MEMBER;
            assertEquals(writeRole, type.minimumRole(ResourceAccessAction.WRITE), "WRITE of " + type);
            assertEquals(type.minimumRole(ResourceAccessAction.WRITE), type.minimumRole(ResourceAccessAction.DELETE),
                    "DELETE of " + type);
        }
    }

    @Test
    void minimumRole_guestAppsLetAGuestReadButNotWrite() {
        assertEquals(OrganizationMemberRole.GUEST, ResourceAccessType.TICKET.minimumRole(ResourceAccessAction.READ));
        assertEquals(OrganizationMemberRole.GUEST,
                ResourceAccessType.KALKAN_PAGE.minimumRole(ResourceAccessAction.READ));
        assertTrue(ResourceAccessType.TICKET.minimumRole(ResourceAccessAction.WRITE)
                .isAtLeast(OrganizationMemberRole.MEMBER));
    }

    @Test
    void constructorGuard_everyTypeWritesAtOrAboveTheAppMinimum() {
        // The enum constructor refuses a lower write role at class load, so a constant that loaded satisfies it.
        for (ResourceAccessType type : ResourceAccessType.values()) {
            for (ResourceAccessAction action : ResourceAccessAction.values()) {
                assertTrue(type.minimumRole(action).isAtLeast(type.getApp().getMinimumRole()),
                        action + " of " + type);
            }
        }
    }

    @Test
    void getApp_mapsEachTypeToItsApp() {
        assertEquals(App.KAZAN, ResourceAccessType.TICKET.getApp());
        assertEquals(App.KAZAN, ResourceAccessType.CHAT_MESSAGE.getApp());
        assertEquals(App.KALKAN, ResourceAccessType.KALKAN_PAGE.getApp());
        assertEquals(App.OTAK, ResourceAccessType.MAIL.getApp());
        assertEquals(App.OTAK, ResourceAccessType.RELATION.getApp());
        assertEquals(App.OTAK, ResourceAccessType.CAMPAIGN.getApp());
    }
}
