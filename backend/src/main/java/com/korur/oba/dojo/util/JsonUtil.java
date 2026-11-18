package com.korur.oba.dojo.util;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.korur.oba.dojo.exception.external.InternalProcessException;
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;

/**
 * JSON serialization and deserialization utility.
 */
public final class JsonUtil {

    private JsonUtil() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Writes dates as ISO 8601 text (an Instant in UTC with Z, a LocalDate as
     * YYYY-MM-DD), the same form as the REST API, not as epoch numbers.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Converts an object to its JSON string representation.
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new InternalProcessException(ExceptionInternalError.JSON_SERIALIZATION, e);
        }
    }

    /**
     * Converts a JSON string to an object of the given class.
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new InternalProcessException(ExceptionInternalError.JSON_SERIALIZATION, e);
        }
    }
}
