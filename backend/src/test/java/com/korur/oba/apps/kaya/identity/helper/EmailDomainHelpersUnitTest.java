package com.korur.oba.apps.kaya.identity.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK

// 4. Internal

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailDomainHelpersUnitTest {

    @Test
    void normalizeEmail_mixedCaseAndSpaces_trimsAndLowerCases() {
        assertEquals("ada@example.com", EmailDomainHelper.normalizeEmail("  Ada@Example.COM "));
    }

    @Test
    void normalizeEmail_null_returnsNull() {
        assertNull(EmailDomainHelper.normalizeEmail(null));
    }
}
