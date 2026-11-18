package com.korur.oba.shrine.storage.enums;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.Optional;

// 4. Internal
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageEntityTypeUnitTest {

    @Test
    void findResourceAccessType_attachableTypes_mapToTheirAccessType() {
        assertEquals(Optional.of(ResourceAccessType.TICKET), StorageEntityType.TICKET.findResourceAccessType());
        assertEquals(Optional.of(ResourceAccessType.CHAT_MESSAGE),
                StorageEntityType.CHAT_MESSAGE.findResourceAccessType());
        assertEquals(Optional.of(ResourceAccessType.KALKAN_PAGE),
                StorageEntityType.KALKAN_PAGE.findResourceAccessType());
        assertEquals(Optional.of(ResourceAccessType.MAIL), StorageEntityType.MAIL.findResourceAccessType());
        assertEquals(Optional.of(ResourceAccessType.RELATION), StorageEntityType.RELATION.findResourceAccessType());
    }

    @Test
    void isUniqueContentPerEntity_mailRelationAndKalkanPage_areTrueOthersFalse() {
        assertTrue(StorageEntityType.MAIL.isUniqueContentPerEntity());
        assertTrue(StorageEntityType.RELATION.isUniqueContentPerEntity());
        assertTrue(StorageEntityType.KALKAN_PAGE.isUniqueContentPerEntity());
        assertFalse(StorageEntityType.TICKET.isUniqueContentPerEntity());
        assertFalse(StorageEntityType.CHAT_MESSAGE.isUniqueContentPerEntity());
    }

    @Test
    void isUniqueContentAcrossType_mailAndRelation_areFalse() {
        assertFalse(StorageEntityType.MAIL.isUniqueContentAcrossType());
        assertFalse(StorageEntityType.RELATION.isUniqueContentAcrossType());
    }

    @Test
    void findResourceAccessType_typesWithoutRules_returnEmpty() {
        assertTrue(StorageEntityType.ORGANIZATION.findResourceAccessType().isEmpty());
        assertTrue(StorageEntityType.USER.findResourceAccessType().isEmpty());
        assertTrue(StorageEntityType.INVOICE.findResourceAccessType().isEmpty());
    }
}
