package com.korur.oba.dojo.logging.filter.unit;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.constant.LoggingConstant;
import com.korur.oba.dojo.logging.filter.RequestLoggingContextFilter;
import com.korur.oba.dojo.logging.mdc.LoggingMdc;

class RequestLoggingContextFilterUnitTest {

    private final RequestLoggingContextFilter filter = new RequestLoggingContextFilter();

    @BeforeEach
    void setUp() {
        LoggingMdc.clearManagedContext();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        LoggingMdc.clearManagedContext();
        SecurityContextHolder.clearContext();
    }

    private String runWithHeader(String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(LoggingConstant.HEADER_ORGANIZATION_ID, header);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return LoggingMdc.get(LoggingConstant.KEY_ORGANIZATION_ID);
    }

    @Test
    void doFilter_validHeader_putsOrganizationId() throws Exception {
        UUID organizationId = UUID.randomUUID();

        assertEquals(organizationId.toString(), runWithHeader(organizationId.toString()));
    }

    @Test
    void doFilter_headerWithSurroundingSpaces_putsTrimmedOrganizationId() throws Exception {
        UUID organizationId = UUID.randomUUID();

        assertEquals(organizationId.toString(), runWithHeader("  " + organizationId + "  "));
    }

    @Test
    void doFilter_malformedHeader_putsUnexpectedValue() throws Exception {
        assertEquals(LoggingConstant.VALUE_UNEXPECTED, runWithHeader("not-a-uuid"));
    }

    @Test
    void doFilter_missingHeader_putsNoneValue() throws Exception {
        assertEquals(LoggingConstant.VALUE_NONE, runWithHeader(null));
    }

    @Test
    void doFilter_blankHeader_putsNoneValue() throws Exception {
        assertEquals(LoggingConstant.VALUE_NONE, runWithHeader("   "));
    }

    @Test
    void doFilter_unauthenticated_putsNoneForAuthFields() throws Exception {
        runWithHeader(null);

        assertEquals(LoggingConstant.VALUE_NONE, LoggingMdc.get(LoggingConstant.KEY_KEYCLOAK_ID));
        assertEquals(LoggingConstant.VALUE_NONE, LoggingMdc.get(LoggingConstant.KEY_ISSUER_REALM));
    }
}
