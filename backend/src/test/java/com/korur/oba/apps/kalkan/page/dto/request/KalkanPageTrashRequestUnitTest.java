package com.korur.oba.apps.kalkan.page.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.ServletRequestDataBinder;

import static org.assertj.core.api.Assertions.assertThat;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;

/**
 * How the query string of GET /kalkan/pages/trash binds.
 */
class KalkanPageTrashRequestUnitTest {

    private static BindingResult bind(KalkanPageTrashRequest query, MockHttpServletRequest request) {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(query);
        binder.setConversionService(new DefaultFormattingConversionService());
        binder.bind(request);
        return binder.getBindingResult();
    }

    @Test
    void noLocale_bindsNull() {
        // Arrange
        KalkanPageTrashRequest query = new KalkanPageTrashRequest();

        // Act
        BindingResult result = bind(query, new MockHttpServletRequest());

        // Assert
        assertThat(result.hasErrors()).isFalse();
        assertThat(query.getLocale()).isNull();
    }

    @Test
    void knownLocale_binds() {
        // Arrange
        KalkanPageTrashRequest query = new KalkanPageTrashRequest();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("locale", "EN");

        // Act
        BindingResult result = bind(query, request);

        // Assert
        assertThat(result.hasErrors()).isFalse();
        assertThat(query.getLocale()).isEqualTo(KalkanLocale.EN);
    }

    @Test
    void unknownLocale_failsBinding() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("locale", "DE");

        // Act
        BindingResult result = bind(new KalkanPageTrashRequest(), request);

        // Assert
        assertThat(result.hasFieldErrors("locale")).isTrue();
    }
}
