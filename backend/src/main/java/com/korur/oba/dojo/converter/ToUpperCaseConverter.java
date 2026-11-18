package com.korur.oba.dojo.converter;

import com.fasterxml.jackson.databind.util.StdConverter;
import com.korur.oba.dojo.annotation.ToUpperCase;

public class ToUpperCaseConverter extends StdConverter<String, String> {
    /**
     * * A converter that transforms strings to uppercase.
     * This is triggered by the {@link ToUpperCase} annotation.
     *
     * @param value The string to convert, or null
     * @return The given string in uppercase, or null if the given string was null
     */
    @Override
    public String convert(String value) {
        if (value == null) {
            return null;
        }
        return value.toUpperCase();
    }
}