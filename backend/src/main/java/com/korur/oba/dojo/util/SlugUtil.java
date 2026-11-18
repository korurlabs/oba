package com.korur.oba.dojo.util;

// 1. Framework-level
import org.springframework.dao.DataIntegrityViolationException;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// 4. Internal

/**
 * Turns a display name into the URL slug that addresses a record in the web app, such as
 * a board, a sprint on a board or a chat channel.
 *
 * The rule is the one the slug migration applied to every existing row, so a name typed
 * today gets the slug its row would have got from the backfill:
 *
 * - Turkish letters and the common accented Latin-1 letters become their plain letter,
 *   then the text is lower-cased.
 * - Each run of characters outside a-z and 0-9 becomes one hyphen, hyphens at both ends
 *   are trimmed, and the result is cut to {@link #MAX_BASE_LENGTH} characters, so a
 *   numeric suffix still fits the 80-character column.
 * - An empty result becomes the caller's fallback word.
 *
 * The transliteration is an explicit letter table, not a Unicode decomposition, because
 * the database applies the same table and both sides must give the same slug for the
 * same name. A letter outside the table counts as a separator on both sides.
 *
 * Uniqueness is the caller's concern: each domain has its own scope and passes its own
 * "taken" check to {@link #firstFree(String, Predicate)}.
 */
public final class SlugUtil {

    /**
     * Longest slug a name produces before a collision suffix is appended.
     */
    public static final int MAX_BASE_LENGTH = 60;

    private static final String TRANSLITERATE_FROM =
            "ıİğĞüÜşŞöÖçÇ"
            + "àáâãäåèéêëìíîïñòóôõùúûýÿ"
            + "ÀÁÂÃÄÅÈÉÊËÌÍÎÏÑÒÓÔÕÙÚÛÝ";

    private static final String TRANSLITERATE_TO =
            "iigguussoocc"
            + "aaaaaaeeeeiiiinoooouuuyy"
            + "AAAAAAEEEEIIIINOOOOUUUY";

    private static final char SEPARATOR = '-';

    private static final Pattern SEPARATOR_RUN = Pattern.compile("[^a-z0-9]+");

    private static final Pattern UUID_SHAPE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static final Pattern COLLISION_SUFFIX = Pattern.compile("[1-9][0-9]*");

    private SlugUtil() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Builds the base slug of a name, before any collision suffix.
     *
     * @param name     the display name, may be null
     * @param fallback the slug to use when the name has no letter or digit left, such as "board"
     * @return the base slug, never empty
     */
    public static String slugify(String name, String fallback) {
        String source = name == null ? "" : name;
        StringBuilder transliterated = new StringBuilder(source.length());
        for (int index = 0; index < source.length(); index++) {
            char letter = source.charAt(index);
            int position = TRANSLITERATE_FROM.indexOf(letter);
            transliterated.append(position < 0 ? letter : TRANSLITERATE_TO.charAt(position));
        }

        String lowered = transliterated.toString().toLowerCase(Locale.ROOT);
        String separated = SEPARATOR_RUN.matcher(lowered).replaceAll(String.valueOf(SEPARATOR));
        String trimmed = trimSeparators(separated);
        if (trimmed.length() > MAX_BASE_LENGTH) {
            trimmed = trimSeparators(trimmed.substring(0, MAX_BASE_LENGTH));
        }
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    /**
     * Finds the first of base, base-1, base-2 and so on that is free.
     *
     * A UUID-shaped candidate is always skipped, so a slug in a path can never be mistaken
     * for a record id.
     *
     * @param base  the base slug from {@link #slugify(String, String)}
     * @param taken tells whether a candidate is already used in the caller's scope, or is
     *              a word the caller reserves
     * @return the first free candidate
     */
    public static String firstFree(String base, Predicate<String> taken) {
        String candidate = base;
        int suffix = 0;
        while (isUuidShaped(candidate) || taken.test(candidate)) {
            suffix++;
            candidate = base + SEPARATOR + suffix;
        }
        return candidate;
    }

    /**
     * Tells whether a slug was built from the given base: it is the base itself, or the
     * base followed by a collision suffix such as "-3". A rename whose new name keeps the
     * base keeps the slug, so a change of letter case or punctuation does not move the URL.
     *
     * @param slug the current slug, may be null
     * @param base the base slug of the new name
     * @return true when the slug can stay
     */
    public static boolean sameBase(String slug, String base) {
        if (slug == null || base == null) {
            return false;
        }
        if (slug.equals(base)) {
            return true;
        }
        String prefix = base + SEPARATOR;
        return slug.startsWith(prefix)
                && COLLISION_SUFFIX.matcher(slug.substring(prefix.length())).matches();
    }

    /**
     * Tells whether an integrity failure is the unique index that keeps a slug free in its
     * scope, and not any other constraint of the row. A save that loses the race for a slug
     * after the free-slug check fails on that index, and only that failure can be answered
     * with a retryable conflict.
     *
     * @param exception the integrity failure of a save
     * @param indexName the name of the slug's unique index, such as "boards_slug_uk"
     * @return true when the failure names that index
     */
    public static boolean isSlugConflict(DataIntegrityViolationException exception, String indexName) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return indexName.equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static boolean isUuidShaped(String candidate) {
        return UUID_SHAPE.matcher(candidate).matches();
    }

    private static String trimSeparators(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == SEPARATOR) {
            start++;
        }
        while (end > start && value.charAt(end - 1) == SEPARATOR) {
            end--;
        }
        return value.substring(start, end);
    }
}
