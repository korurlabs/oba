package com.korur.oba.dojo.security.filter;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// 2. Third-party
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.TestTokenKinds;
import com.korur.oba.dojo.security.token.authentication.ApiTokenAuthentication;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.helper.ApiTokenKindRegistry;
import com.korur.oba.dojo.security.token.service.ApiTokenQueryService;
import com.korur.oba.dojo.security.token.service.ApiTokenService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTokenAuthenticationFilterUnitTest {

    private static final String AGENT_TOKEN = "oat_" + "A".repeat(43);
    private static final String MACHINE_TOKEN = "kyt_" + "B".repeat(43);

    @Mock
    private ApiTokenQueryService apiTokenQueryService;

    @Mock
    private ApiTokenService apiTokenService;

    @Mock
    private LoggingService loggingService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TestTokenKinds.MachineLikeHandler machineHandler = new TestTokenKinds.MachineLikeHandler();
    private ApiTokenKindRegistry registry;
    private ApiTokenAuthenticationFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        registry = TestTokenKinds.registryOf(machineHandler, new TestTokenKinds.AgentLikeHandler());
        filter = new ApiTokenAuthenticationFilter(registry, apiTokenQueryService, apiTokenService, objectMapper,
                loggingService);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request(String method, String path, String bearer) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (bearer != null) {
            request.addHeader("Authorization", "Bearer " + bearer);
        }
        return request;
    }

    private static ApiToken token(ApiTokenKind kind) {
        return ApiToken.builder()
                .id(UUID.randomUUID())
                .kind(kind)
                .subjectId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .organizationId(UUID.randomUUID())
                .build();
    }

    private String errorCode() throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        return body.path("error").path("code").asText();
    }

    @Test
    void matcher_selectsAcceptedPrefixesAndReservedPathsOnly() {
        ApiTokenRequestMatcher matcher = new ApiTokenRequestMatcher(registry);

        assertTrue(matcher.matches(request("GET", "/kazan/tickets", AGENT_TOKEN)));
        assertTrue(matcher.matches(request("POST", "/kaya/ingest/batches", "eyJhbGciOiJSUzI1NiJ9.e30.sig")));
        assertFalse(matcher.matches(request("GET", "/kazan/tickets", "eyJhbGciOiJSUzI1NiJ9.e30.sig")));
        assertFalse(matcher.matches(request("GET", "/kazan/tickets", null)));
    }

    @Test
    void matcher_noKindRegistered_selectsNothing() {
        ApiTokenRequestMatcher matcher = new ApiTokenRequestMatcher(TestTokenKinds.registryOf());

        assertFalse(matcher.matches(request("GET", "/kazan/tickets", AGENT_TOKEN)));
        assertFalse(matcher.matches(request("POST", "/kaya/ingest/batches", MACHINE_TOKEN)));
    }

    @Test
    void doFilter_liveAgentToken_setsTheKindsPrincipalAndMarksTheUse() throws Exception {
        ApiToken token = token(ApiTokenKind.AGENT);
        when(apiTokenQueryService.authenticate(AGENT_TOKEN)).thenReturn(token);
        MockHttpServletRequest request = request("GET", "/kazan/tickets", AGENT_TOKEN);

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApiTokenAuthentication tokenAuthentication = assertInstanceOf(ApiTokenAuthentication.class, authentication);
        assertEquals(ApiTokenKind.AGENT, tokenAuthentication.getKind());
        assertEquals(token.getUserId(), tokenAuthentication.getPrincipal().userId());
        assertEquals(token.getOrganizationId(), tokenAuthentication.getPrincipal().organizationId());
        assertNull(tokenAuthentication.getCredentials());
        verify(apiTokenService).markUsed(token.getId());
    }

    @Test
    void doFilter_jwtOnAReservedPath_answersTheReservingKindsError() throws Exception {
        filter.doFilter(request("GET", "/kaya/delivery/conventions", "eyJhbGciOiJSUzI1NiJ9.e30.sig"), response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("TST_AUT_001", errorCode());
        assertNull(chain.getRequest());
        verifyNoInteractions(apiTokenQueryService);
    }

    @Test
    void doFilter_agentTokenOnAPathReservedForMachines_isRefused() throws Exception {
        filter.doFilter(request("POST", "/kaya/ingest/batches", AGENT_TOKEN), response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("TST_AUT_001", errorCode());
        verifyNoInteractions(apiTokenQueryService);
    }

    @Test
    void doFilter_machineTokenOutsideItsReservedPaths_isRefused() throws Exception {
        filter.doFilter(request("GET", "/kazan/tickets", MACHINE_TOKEN), response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("TST_AUT_001", errorCode());
        verifyNoInteractions(apiTokenQueryService);
    }

    @Test
    void doFilter_refusedToken_answersTheKindsErrorAndMarksNothing() throws Exception {
        when(apiTokenQueryService.authenticate(AGENT_TOKEN)).thenThrow(new UnauthorizedException());

        filter.doFilter(request("GET", "/kazan/tickets", AGENT_TOKEN), response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("TST_AUT_002", errorCode());
        verify(apiTokenService, never()).markUsed(any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_subjectNoLongerActive_answersTheKindsError() throws Exception {
        ApiToken token = token(ApiTokenKind.KAYA_MACHINE);
        machineHandler.retiredSubjectId = token.getSubjectId();
        when(apiTokenQueryService.authenticate(MACHINE_TOKEN)).thenReturn(token);

        filter.doFilter(request("POST", "/kaya/ingest/batches", MACHINE_TOKEN), response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("TST_AUT_001", errorCode());
        verify(apiTokenService, never()).markUsed(any());
    }

    @Test
    void doFilter_bodyOverTheKindsLimit_answers413AfterMarkingTheUse() throws Exception {
        ApiToken token = token(ApiTokenKind.KAYA_MACHINE);
        when(apiTokenQueryService.authenticate(MACHINE_TOKEN)).thenReturn(token);
        MockHttpServletRequest request = request("POST", "/kaya/ingest/batches", MACHINE_TOKEN);
        request.setContent("x".repeat(TestTokenKinds.MACHINE_BODY_LIMIT + 1).getBytes(StandardCharsets.UTF_8));

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertEquals("TST_API_001", errorCode());
        verify(apiTokenService).markUsed(token.getId());
        assertNull(chain.getRequest());
    }

    @Test
    void doFilter_bodyWithinTheKindsLimit_isServedAgainToTheController() throws Exception {
        ApiToken token = token(ApiTokenKind.KAYA_MACHINE);
        when(apiTokenQueryService.authenticate(MACHINE_TOKEN)).thenReturn(token);
        MockHttpServletRequest request = request("POST", "/kaya/ingest/batches", MACHINE_TOKEN);
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        request.setContent(body);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(new String(body, StandardCharsets.UTF_8),
                new String(chain.getRequest().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }
}
