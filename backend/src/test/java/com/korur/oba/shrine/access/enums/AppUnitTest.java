package com.korur.oba.shrine.access.enums;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 3. Java SDK
import java.util.Optional;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

class AppUnitTest {

    @Test
    void fromAppPackage_knownSegments_mapIgnoringCase() {
        assertEquals(Optional.of(App.KAZAN), App.fromAppPackage("kazan"));
        assertEquals(Optional.of(App.KAYA), App.fromAppPackage("kaya"));
        assertEquals(Optional.of(App.KAYA), App.fromAppPackage("Kaya"));
        assertEquals(Optional.of(App.KALKAN), App.fromAppPackage("kalkan"));
        assertEquals(Optional.of(App.OTAK), App.fromAppPackage("otak"));
    }

    @Test
    void fromAppPackage_unknownBlankOrNullSegment_returnsEmpty() {
        assertTrue(App.fromAppPackage("newapp").isEmpty());
        assertTrue(App.fromAppPackage(" ").isEmpty());
        assertTrue(App.fromAppPackage(null).isEmpty());
    }

    @Test
    void getMinimumRole_eachApp_returnsItsMinimumRole() {
        assertEquals(OrganizationMemberRole.GUEST, App.KAZAN.getMinimumRole());
        assertEquals(OrganizationMemberRole.MEMBER, App.KAYA.getMinimumRole());
        assertEquals(OrganizationMemberRole.GUEST, App.KALKAN.getMinimumRole());
        assertEquals(OrganizationMemberRole.MEMBER, App.OTAK.getMinimumRole());
    }
}
