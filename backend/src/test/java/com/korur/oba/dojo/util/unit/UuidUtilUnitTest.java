package com.korur.oba.dojo.util.unit;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.util.UuidUtil;

class UuidUtilUnitTest {

    @Test
    void parse_nullValue_returnsEmpty() {
        // Act
        Optional<UUID> result = UuidUtil.parse(null);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void parse_blankValue_returnsEmpty() {
        // Act
        Optional<UUID> result = UuidUtil.parse("   ");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void parse_valueWithSurroundingSpaces_returnsTrimmedUuid() {
        // Arrange
        UUID expected = UUID.randomUUID();

        // Act
        Optional<UUID> result = UuidUtil.parse("  " + expected + "\t");

        // Assert
        assertEquals(Optional.of(expected), result);
    }

    @Test
    void parse_malformedValue_returnsEmpty() {
        // Act
        Optional<UUID> result = UuidUtil.parse("not-a-uuid");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void parse_validValue_returnsUuid() {
        // Arrange
        UUID expected = UUID.randomUUID();

        // Act
        Optional<UUID> result = UuidUtil.parse(expected.toString());

        // Assert
        assertEquals(Optional.of(expected), result);
    }
}
