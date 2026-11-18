package com.korur.oba.apps.kaya.core.security.filter;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.fasterxml.jackson.databind.ObjectMapper;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.service.MachineService;
import com.korur.oba.apps.kaya.machine.service.MachineTokenQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class MachineTokenAuthenticationFilterUnitTest {

    @Mock
    private MachineTokenQueryService machineTokenQueryService;

    @Mock
    private MachineService machineService;

    @Mock
    private KayaUserQueryService kayaUserQueryService;

    @Mock
    private OrganizationAccessQueryService organizationAccessQueryService;

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @Mock
    private LoggingService loggingService;

    private MachineTokenAuthenticationFilter filter;
    private MachinePrincipal principal;
    private User user;

    @BeforeEach
    void setUp() {
        filter = new MachineTokenAuthenticationFilter(machineTokenQueryService, machineService, kayaUserQueryService,
                organizationAccessQueryService, appAccessQueryService, new ObjectMapper(), loggingService);
        principal = new MachinePrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "laptop");
        user = User.builder().build();
        user.setId(principal.userId());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        request.addHeader("Authorization", "Bearer token-1");
        return request;
    }

    private void authenticate() {
        when(machineTokenQueryService.authenticate("token-1")).thenReturn(principal);
    }

    @Nested
    class IngestPath {

        @Test
        void validToken_skipsTheDeliveryChecksAndPassesTheRequestOn() throws Exception {
            authenticate();
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request("/kaya/ingest/batches"), response, chain);

            assertEquals(200, response.getStatus());
            assertNotNull(chain.getRequest());
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            verify(machineService).markTokenUsed(principal.tokenId());
            verifyNoInteractions(kayaUserQueryService, organizationAccessQueryService, appAccessQueryService);
        }

        @Test
        void percentEncodedIngestPath_isDecodedAndStillCountsAsIngest() throws Exception {
            authenticate();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request("/kaya/%69ngest/batches"), new MockHttpServletResponse(), chain);

            assertNotNull(chain.getRequest());
            verifyNoInteractions(appAccessQueryService);
        }
    }

    @Nested
    class DeliveryPath {

        @Test
        void tenantAndKayaAccessGranted_checksTenantBeforeAppAndPassesTheRequestOn() throws Exception {
            authenticate();
            when(kayaUserQueryService.getEntityById(principal.userId())).thenReturn(user);
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request("/kaya/config/agents"), response, chain);

            InOrder order = inOrder(organizationAccessQueryService, appAccessQueryService);
            order.verify(organizationAccessQueryService).verifyTenantAccess(user, principal.organizationId());
            order.verify(appAccessQueryService).verifyAppAccess(principal.organizationId(), user.getId(), App.KAYA);
            assertEquals(200, response.getStatus());
            assertNotNull(chain.getRequest());
        }

        @Test
        void tenantRefused_answers403AndNeverChecksTheApp() throws Exception {
            authenticate();
            when(kayaUserQueryService.getEntityById(principal.userId())).thenReturn(user);
            doThrow(new ForbiddenException()).when(organizationAccessQueryService)
                    .verifyTenantAccess(user, principal.organizationId());
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request("/kaya/config/agents"), response, chain);

            assertEquals(403, response.getStatus());
            assertNull(chain.getRequest());
            verifyNoInteractions(appAccessQueryService);
        }

        @Test
        void kayaAccessRefused_answers403WithAppAccessDeniedCodeAndDoesNotContinue() throws Exception {
            authenticate();
            when(kayaUserQueryService.getEntityById(principal.userId())).thenReturn(user);
            doThrow(new ForbiddenException(ExceptionDomainAccessError.SEC_APP_ACCESS_DENIED))
                    .when(appAccessQueryService).verifyAppAccess(principal.organizationId(), user.getId(), App.KAYA);
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request("/kaya/config/agents"), response, chain);

            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("APP_SEC_001"));
            assertNull(chain.getRequest());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        void pathThatOnlyStartsLikeIngest_isStillChecked() throws Exception {
            authenticate();
            when(kayaUserQueryService.getEntityById(principal.userId())).thenReturn(user);

            filter.doFilter(request("/kaya/ingestion/x"), new MockHttpServletResponse(), new MockFilterChain());

            verify(appAccessQueryService).verifyAppAccess(principal.organizationId(), user.getId(), App.KAYA);
        }
    }

    @Nested
    class MissingToken {

        @Test
        void noAuthorizationHeader_answers401WithoutAnyLookup() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/kaya/config/agents");
            request.setRequestURI("/kaya/config/agents");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertEquals(401, response.getStatus());
            assertTrue(response.getContentAsString().contains("KAY_SEC_003"));
            verify(machineService, never()).markTokenUsed(any());
            verifyNoInteractions(appAccessQueryService);
        }
    }
}
