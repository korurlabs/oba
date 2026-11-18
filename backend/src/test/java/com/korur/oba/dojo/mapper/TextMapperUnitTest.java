package com.korur.oba.dojo.mapper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextMapperUnitTest {

    @Test
    void trimText_surroundingWhitespace_returnsTrimmedText() {
        // Act
        String result = TextMapper.trimText(" \tEngineering  ");

        // Assert
        assertEquals("Engineering", result);
    }

    @Test
    void trimText_nullValue_returnsNull() {
        // Act
        String result = TextMapper.trimText(null);

        // Assert
        assertNull(result);
    }
}
