package com.korur.oba.dojo.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Named;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Shared MapStruct utility for text values that every domain stores the same way.
 *
 * Reference this class in the uses clause of a mapper and qualify a mapping by the {@link Named} value
 * of the method, so that MapStruct never applies it to a String field on its own.
 */
public final class TextMapper {

    private TextMapper() {
        throw new UnsupportedOperationException(CommonConstant.UTILITY_CLASS_MESSAGE);
    }

    /**
     * Trims a name or description before it is stored. A case-insensitive name check compares the stored
     * names, so "Engineering " would pass as a second "Engineering" if the spaces were kept. Trimming uses
     * the same whitespace rule as the blank checks of the requests, so a value that passed validation stays
     * non-empty.
     *
     * @param value the text, may be null
     * @return the trimmed text, or null when the text is null
     */
    @Named("trimText")
    public static String trimText(String value) {
        return value == null ? null : value.trim();
    }
}
