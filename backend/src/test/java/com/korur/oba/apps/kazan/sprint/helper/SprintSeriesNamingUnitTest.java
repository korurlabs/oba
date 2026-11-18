package com.korur.oba.apps.kazan.sprint.helper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SprintSeriesNamingUnitTest {

    private void assertSplit(String sprintName, String expectedName, int expectedRound) {
        SprintSeriesNaming.SeriesName result = SprintSeriesNaming.split(sprintName);

        assertEquals(expectedName, result.name());
        assertEquals(expectedRound, result.round());
    }

    @Nested
    class Split {

        @Test
        void nameWithTrailingOne_splitsRoundOne() {
            assertSplit("Atasprint 1", "Atasprint", 1);
        }

        @Test
        void nameWithoutRound_keepsWholeNameAndRoundOne() {
            assertSplit("Atasprint", "Atasprint", 1);
        }

        @Test
        void nameWithTrailingSeven_splitsRoundSeven() {
            assertSplit("Sprint 7", "Sprint", 7);
        }

        @Test
        void nameWithDigitsInside_splitsOnlyTheTrailingRound() {
            assertSplit("Q4 2026 sprint 3", "Q4 2026 sprint", 3);
        }

        @Test
        void roundZero_keepsWholeNameAndRoundOne() {
            assertSplit("Atasprint 0", "Atasprint 0", 1);
        }

        @Test
        void sevenDigitRound_keepsWholeNameAndRoundOne() {
            assertSplit("X 1234567", "X 1234567", 1);
        }

        @Test
        void sixDigitRound_isSplit() {
            assertSplit("X 999999", "X", 999999);
        }

        @Test
        void surroundingWhitespace_isTrimmed() {
            assertSplit("  Release prep  4 ", "Release prep", 4);
        }

        @Test
        void onlyDigits_keepsWholeNameAndRoundOne() {
            assertSplit("42", "42", 1);
        }
    }

    @Nested
    class SprintName {

        @Test
        void seriesNameAndRound_joinsWithSingleSpace() {
            assertEquals("Atasprint 3", SprintSeriesNaming.sprintName("Atasprint", 3));
        }
    }
}
