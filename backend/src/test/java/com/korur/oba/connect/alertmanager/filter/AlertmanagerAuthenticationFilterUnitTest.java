package com.korur.oba.connect.alertmanager.filter;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.alertmanager.constant.AlertmanagerConstant;
import com.korur.oba.connect.alertmanager.constant.security.AlertmanagerSecurityLogConstant;
import com.korur.oba.connect.alertmanager.dto.AlertmanagerPrincipal;
import com.korur.oba.connect.alertmanager.validator.AlertmanagerTokenValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class AlertmanagerAuthenticationFilterUnitTest {

    private static final String HEADER = "Bearer s3cret-token-value";

    @Mock
    private AlertmanagerTokenValidator tokenValidator;

    @Mock
    private LoggingService loggingService;

    private final UUID organizationId = UUID.randomUUID();

    private AlertmanagerAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new AlertmanagerAuthenticationFilter(tokenValidator, loggingService);
        request = new MockHttpServletRequest("POST", "/kazan/integrations/alertmanager");
        request.addHeader(HttpHeaders.AUTHORIZATION, HEADER);
        request.setRemoteAddr("10.0.0.7");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void assertLoggedOutcome(String outcome) {
        ArgumentCaptor<Object> arguments = ArgumentCaptor.forClass(Object.class);
        verify(loggingService).info(any(), eq(LogLayer.FILTER), any(String.class),
                arguments.capture(), arguments.capture(), arguments.capture(), arguments.capture());
        assertThat(arguments.getAllValues()).doesNotContain(HEADER).doesNotContain("s3cret-token-value");
        assertThat(arguments.getAllValues()).contains(outcome);
    }

    @Test
    void doFilter_tokenMatches_setsPrincipalWithBoundOrganization() throws Exception {
        when(tokenValidator.resolveOrganizationId(HEADER)).thenReturn(Optional.of(organizationId));

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(new AlertmanagerPrincipal(organizationId));
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly(AlertmanagerConstant.AUTHORITY_INTEGRATION_ALERTMANAGER);
        assertThat(chain.getRequest()).isSameAs(request);
        assertLoggedOutcome(AlertmanagerSecurityLogConstant.OUTCOME_AUTHENTICATED);
    }

    @Test
    void doFilter_tokenDoesNotMatch_leavesContextEmptyAndContinuesChain() throws Exception {
        when(tokenValidator.resolveOrganizationId(HEADER)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        assertLoggedOutcome(AlertmanagerSecurityLogConstant.OUTCOME_REJECTED);
    }
}
