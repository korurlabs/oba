package com.korur.oba.apps.kalkan.page.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.ServletRequestDataBinder;

import static org.assertj.core.api.Assertions.assertThat;

// 2. Third-party
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

// 3. Java SDK
import java.util.Set;

// 4. Internal
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;

/**
 * How the query string of GET /kalkan/pages/recent binds and validates.
 */
class KalkanPageRecentRequestUnitTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static BindingResult bind(KalkanPageRecentRequest query, MockHttpServletRequest request) {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(query);
        binder.setConversionService(new DefaultFormattingConversionService());
        binder.bind(request);
        return binder.getBindingResult();
    }

    private static MockHttpServletRequest params(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(name, value);
        return request;
    }

    @Test
    void noParameters_defaultsToTwentyWithoutLocale() {
        // Arrange
        KalkanPageRecentRequest query = new KalkanPageRecentRequest();

        // Act
        BindingResult result = bind(query, new MockHttpServletRequest());

        // Assert
        assertThat(result.hasErrors()).isFalse();
        assertThat(query.getLimit()).isEqualTo(20);
        assertThat(query.getLocale()).isNull();
        assertThat(validator.validate(query)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "100"})
    void limitAtTheBounds_isValid(String limit) {
        // Arrange
        KalkanPageRecentRequest query = new KalkanPageRecentRequest();

        // Act
        BindingResult result = bind(query, params("limit", limit));

        // Assert
        assertThat(result.hasErrors()).isFalse();
        assertThat(query.getLimit()).isEqualTo(Integer.parseInt(limit));
        assertThat(validator.validate(query)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "-5"})
    void limitOutsideTheBounds_isRefused(String limit) {
        // Arrange
        KalkanPageRecentRequest query = new KalkanPageRecentRequest();
        bind(query, params("limit", limit));

        // Act
        Set<ConstraintViolation<KalkanPageRecentRequest>> violations = validator.validate(query);

        // Assert
        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("limit");
    }

    @Test
    void limitNotANumber_failsBinding() {
        // Act
        BindingResult result = bind(new KalkanPageRecentRequest(), params("limit", "many"));

        // Assert
        assertThat(result.hasFieldErrors("limit")).isTrue();
    }

    @Test
    void knownLocale_binds() {
        // Arrange
        KalkanPageRecentRequest query = new KalkanPageRecentRequest();

        // Act
        BindingResult result = bind(query, params("locale", "TR"));

        // Assert
        assertThat(result.hasErrors()).isFalse();
        assertThat(query.getLocale()).isEqualTo(KalkanLocale.TR);
    }

    @Test
    void unknownLocale_failsBinding() {
        // Act
        BindingResult result = bind(new KalkanPageRecentRequest(), params("locale", "DE"));

        // Assert
        assertThat(result.hasFieldErrors("locale")).isTrue();
    }
}
