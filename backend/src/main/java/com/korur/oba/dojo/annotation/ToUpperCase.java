package com.korur.oba.dojo.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.korur.oba.dojo.converter.ToUpperCaseConverter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to automatically convert a field's value to uppercase during JSON deserialization.
 * This annotation can be applied to fields to enforce the conversion.
 * <p>
 * The conversion is handled by the {@link ToUpperCaseConverter} class, which transforms
 * the annotated element's value to uppercase.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @ToUpperCase
 * private String name;
 * }
 * </pre>
 * </p>
 * <p>
 * This annotation simplifies the conversion of Strings to uppercase, eliminating the need
 * to manually call the String.toUpperCase() method throughout the codebase.
 * </p>
 * <p>
 * This annotation supports JSON deserialization with Jackson.
 * </p>
 *
 * @see ToUpperCaseConverter
 */
@JacksonAnnotationsInside
@JsonDeserialize(converter = ToUpperCaseConverter.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToUpperCase {
}