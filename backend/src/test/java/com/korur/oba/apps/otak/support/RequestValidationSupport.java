package com.korur.oba.apps.otak.support;

// 2. Third-party
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

// 3. Java SDK
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test helper for the request validation unit tests: runs Bean Validation on a request and copies a request record
 * with one component replaced, so a test states only the field it changes.
 */
public final class RequestValidationSupport {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    private RequestValidationSupport() {
    }

    /**
     * Returns every violation of the request as {@code property: message}.
     */
    public static Set<String> violations(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toSet());
    }

    /**
     * Returns a copy of the record with the named component replaced by the given value.
     */
    public static <T extends Record> T with(T record, String component, Object value) {
        try {
            RecordComponent[] components = record.getClass().getRecordComponents();
            Class<?>[] types = new Class<?>[components.length];
            Object[] values = new Object[components.length];
            boolean found = false;
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                if (components[i].getName().equals(component)) {
                    values[i] = value;
                    found = true;
                } else {
                    values[i] = components[i].getAccessor().invoke(record);
                }
            }
            if (!found) {
                throw new IllegalArgumentException("No record component named " + component);
            }
            Constructor<?> constructor = record.getClass().getDeclaredConstructor(types);
            @SuppressWarnings("unchecked")
            T copy = (T) constructor.newInstance(values);
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not copy " + record.getClass().getSimpleName(), e);
        }
    }
}
