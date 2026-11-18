package com.korur.oba.dojo.util.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

// 4. Internal
import com.korur.oba.dojo.util.SlugUtil;

class SlugUtilUnitTest {

    @Nested
    class Slugify {

        @Test
        void plainName_isLowerCasedAndHyphenated() {
            assertEquals("my-board", SlugUtil.slugify("My Board", "board"));
        }

        @Test
        void turkishLetters_becomePlainLetters() {
            assertEquals("iigguussoocc", SlugUtil.slugify("ıİğĞüÜşŞöÖçÇ", "board"));
            assertEquals("istanbul-guzel-sehir", SlugUtil.slugify("İstanbul Güzel Şehir", "board"));
        }

        @Test
        void latin1AccentedLetters_becomePlainLetters() {
            assertEquals("aaaaaaeeeeiiiinoooouuuyy", SlugUtil.slugify("àáâãäåèéêëìíîïñòóôõùúûýÿ", "board"));
            assertEquals("aaaaaaeeeeiiiinoooouuuy", SlugUtil.slugify("ÀÁÂÃÄÅÈÉÊËÌÍÎÏÑÒÓÔÕÙÚÛÝ", "board"));
        }

        @Test
        void letterOutsideTheTable_isASeparator() {
            assertEquals("a-b", SlugUtil.slugify("aøb", "board"));
        }

        @Test
        void runsOfSymbols_becomeOneHyphen() {
            assertEquals("a-b-c", SlugUtil.slugify("a  --  b!!!c", "board"));
        }

        @Test
        void leadingAndTrailingSeparators_areTrimmed() {
            assertEquals("hello", SlugUtil.slugify("  --hello!! ", "board"));
        }

        @Test
        void digits_areKept() {
            assertEquals("q4-2026", SlugUtil.slugify("Q4 2026", "board"));
        }

        @Test
        void nameLongerThanSixty_isCutToSixtyCharacters() {
            String name = "a".repeat(100);

            String slug = SlugUtil.slugify(name, "board");

            assertEquals(60, slug.length());
        }

        @Test
        void cutLandingOnASeparator_dropsTheTrailingHyphen() {
            String name = "a".repeat(59) + " bbbbb";

            String slug = SlugUtil.slugify(name, "board");

            assertEquals("a".repeat(59), slug);
        }

        @Test
        void nameOfExactlySixty_isNotCut() {
            String name = "a".repeat(60);

            assertEquals(name, SlugUtil.slugify(name, "board"));
        }

        @Test
        void emojiOnlyName_returnsFallback() {
            assertEquals("board", SlugUtil.slugify("🚀🔥", "board"));
        }

        @Test
        void nullName_returnsFallback() {
            assertEquals("sprint", SlugUtil.slugify(null, "sprint"));
        }

        @Test
        void blankName_returnsFallback() {
            assertEquals("channel", SlugUtil.slugify("   ", "channel"));
        }
    }

    @Nested
    class FirstFree {

        @Test
        void baseFree_returnsBase() {
            assertEquals("alpha", SlugUtil.firstFree("alpha", candidate -> false));
        }

        @Test
        void baseTaken_returnsSuffixOne() {
            Set<String> taken = Set.of("alpha");

            assertEquals("alpha-1", SlugUtil.firstFree("alpha", taken::contains));
        }

        @Test
        void baseAndSuffixOneTaken_returnsSuffixTwo() {
            Set<String> taken = Set.of("alpha", "alpha-1");

            assertEquals("alpha-2", SlugUtil.firstFree("alpha", taken::contains));
        }

        @Test
        void uuidShapedBase_isSkippedEvenWhenFree() {
            String uuidShaped = "123e4567-e89b-12d3-a456-426614174000";

            assertEquals(uuidShaped + "-1", SlugUtil.firstFree(uuidShaped, candidate -> false));
        }

        @Test
        void reservedWord_isSkippedThroughTheTakenCheck() {
            Set<String> reserved = new HashSet<>(Set.of("all", "all-1"));

            assertEquals("all-2", SlugUtil.firstFree("all", reserved::contains));
        }

        @Test
        void sixtyCharacterBaseWithSuffix_fitsTheEightyCharacterColumn() {
            String base = "a".repeat(60);

            String slug = SlugUtil.firstFree(base, candidate -> candidate.equals(base));

            assertEquals(base + "-1", slug);
            assertTrue(slug.length() <= 80);
        }
    }

    @Nested
    class SameBase {

        @Test
        void identicalSlug_isSameBase() {
            assertTrue(SlugUtil.sameBase("alpha", "alpha"));
        }

        @Test
        void collisionSuffix_isSameBase() {
            assertTrue(SlugUtil.sameBase("alpha-3", "alpha"));
            assertTrue(SlugUtil.sameBase("alpha-12", "alpha"));
        }

        @Test
        void zeroOrLeadingZeroSuffix_isNotSameBase() {
            assertFalse(SlugUtil.sameBase("alpha-0", "alpha"));
            assertFalse(SlugUtil.sameBase("alpha-01", "alpha"));
        }

        @Test
        void textSuffix_isNotSameBase() {
            assertFalse(SlugUtil.sameBase("alpha-beta", "alpha"));
        }

        @Test
        void differentBase_isNotSameBase() {
            assertFalse(SlugUtil.sameBase("beta", "alpha"));
        }

        @Test
        void longerBaseSharingThePrefix_isNotSameBase() {
            assertFalse(SlugUtil.sameBase("alphabet", "alpha"));
        }

        @Test
        void nullSlugOrBase_isNotSameBase() {
            assertFalse(SlugUtil.sameBase(null, "alpha"));
            assertFalse(SlugUtil.sameBase("alpha", null));
        }
    }

    @Nested
    class IsSlugConflict {

        private static final String INDEX = "boards_slug_uk";

        @Test
        void violationOfTheNamedIndex_returnsTrue() {
            assertTrue(SlugUtil.isSlugConflict(violation(INDEX), INDEX));
        }

        @Test
        void indexNameCase_isIgnored() {
            assertTrue(SlugUtil.isSlugConflict(violation("BOARDS_SLUG_UK"), INDEX));
        }

        @Test
        void violationOfAnotherConstraint_returnsFalse() {
            assertFalse(SlugUtil.isSlugConflict(violation("boards_prefix_uk"), INDEX));
        }

        @Test
        void failureWithoutConstraintViolationCause_returnsFalse() {
            DataIntegrityViolationException exception =
                    new DataIntegrityViolationException("failed", new SQLException("other"));

            assertFalse(SlugUtil.isSlugConflict(exception, INDEX));
        }

        @Test
        void nullConstraintName_returnsFalse() {
            assertFalse(SlugUtil.isSlugConflict(violation(null), INDEX));
        }

        private DataIntegrityViolationException violation(String constraintName) {
            return new DataIntegrityViolationException("could not execute statement",
                    new ConstraintViolationException("duplicate key", new SQLException("duplicate key"),
                            constraintName));
        }
    }

    @Test
    void constructor_utilityClass_throwsUnsupportedOperation() throws Exception {
        java.lang.reflect.Constructor<SlugUtil> constructor = SlugUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception =
                assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        assertTrue(exception.getCause() instanceof UnsupportedOperationException);
    }
}
