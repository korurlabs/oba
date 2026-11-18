package com.korur.oba.dojo.security.interceptor;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

// 3. Java SDK
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.dojo.security.constant.SecurityLogConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.validator.UserAuthorizationValidator;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequiredUserAccessInterceptorUnitTest {

    private static final String SUBJECT = "kc-subject";

    /** A controller of the application package, so the interceptor treats it as guarded. */
    static class Fixture {

        @RequiredUserAccess(OrganizationMemberRole.ADMIN)
        public void adminOnly() {
        }

        @RequiredUserAccess(OrganizationMemberRole.GUEST)
        public void guestOk() {
        }

        public void unannotated() {
        }
    }

    @Mock
    private UserAuthorizationValidator validator;

    @Mock
    private LoggingService loggingService;

    private RequiredUserAccessInterceptor interceptor;
    private MockHttpServletRequest request;
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final UUID organizationId = UUID.randomUUID();
    private final User user = User.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        interceptor = new RequiredUserAccessInterceptor(validator, loggingService);
        request = new MockHttpServletRequest("GET", "/kaizen/chat/channels");
        signInWithJwt();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void signInWithJwt() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = Fixture.class.getMethod(methodName);
        return new HandlerMethod(new Fixture(), method);
    }

    private OrganizationMember member(OrganizationMemberRole role) {
        return OrganizationMember.builder()
                .organization(Organization.builder().id(organizationId).build())
                .user(user)
                .role(role)
                .build();
    }

    /** Stubs every validator step to pass for the given role and returns the membership. */
    private OrganizationMember passingChain(OrganizationMemberRole role) {
        OrganizationMember member = member(role);
        when(validator.ensureUserIsProvisioned(SUBJECT, request.getRequestURI())).thenReturn(user);
        when(validator.ensureOrganizationIdIsValid(any(), any(), anyString())).thenReturn(organizationId);
        when(validator.ensureUserIsMember(user.getId(), organizationId, request.getRequestURI())).thenReturn(member);
        return member;
    }

    @Nested
    class PassThrough {

        @Test
        void preHandle_nonHandlerMethod_passes() {
            assertTrue(assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object())));
            verifyNoInteractions(validator);
        }

        @Test
        void preHandle_handlerOutsideTheApplicationPackage_passes() throws Exception {
            HandlerMethod foreign = new HandlerMethod(new Object(), Object.class.getMethod("toString"));

            assertTrue(interceptor.preHandle(request, response, foreign));
            verifyNoInteractions(validator);
        }

        @Test
        void preHandle_authenticationIsNotAJwt_passes() throws Exception {
            SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                    "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

            assertTrue(interceptor.preHandle(request, response, handler("adminOnly")));
            verifyNoInteractions(validator);
        }

        @Test
        void preHandle_noAuthentication_passes() throws Exception {
            SecurityContextHolder.clearContext();

            assertTrue(interceptor.preHandle(request, response, handler("adminOnly")));
            verifyNoInteractions(validator);
        }

        @Test
        void preHandle_unannotatedExemptPath_passesWithoutChecks() throws Exception {
            request.setRequestURI("/users/me");

            assertTrue(interceptor.preHandle(request, response, handler("unannotated")));
            verifyNoInteractions(validator);
        }

        @Test
        void preHandle_unannotatedPublicPath_passesWithoutChecks() throws Exception {
            request.setRequestURI("/invitations/preview");

            assertTrue(interceptor.preHandle(request, response, handler("unannotated")));
            verifyNoInteractions(validator);
        }

        @Test
        void preHandle_exemptPathBehindAContextPath_matchesTheContextRelativePath() throws Exception {
            request.setContextPath("/api");
            request.setRequestURI("/api/users/me");

            assertTrue(interceptor.preHandle(request, response, handler("unannotated")));
        }
    }

    @Nested
    class DefaultDeny {

        @Test
        void preHandle_unannotatedUnlistedPath_throwsHandlerUnannotated() throws Exception {
            request.setRequestURI("/kaizen/chat/channels");

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> interceptor.preHandle(request, response, handler("unannotated")));

            assertEquals("GEN_SEC_004", exception.getErrorCode());
            verifyNoInteractions(validator);
            verify(loggingService).error(any(LogPrefix.class), eq(LogLayer.HANDLER),
                    eq(SecurityLogConstant.ACCESS_DENIED_UNANNOTATED), any(HandlerMethod.class), eq("/kaizen/chat/channels"));
        }
    }

    @Nested
    class CheckOrder {

        @Test
        void preHandle_allChecksPass_runsTheSixStepsInOrderAndStoresThePrincipal() throws Exception {
            OrganizationMember member = passingChain(OrganizationMemberRole.OWNER);
            request.addHeader("X-Organization-ID", organizationId.toString());

            boolean result = interceptor.preHandle(request, response, handler("adminOnly"));

            assertTrue(result);
            InOrder order = inOrder(validator);
            order.verify(validator).ensureUserIsProvisioned(SUBJECT, "/kaizen/chat/channels");
            order.verify(validator).ensureUserIsActive(user, "/kaizen/chat/channels");
            order.verify(validator).ensureOrganizationIdIsValid(null, organizationId.toString(), "/kaizen/chat/channels");
            order.verify(validator).ensureUserIsMember(user.getId(), organizationId, "/kaizen/chat/channels");
            order.verify(validator).ensureMembershipIsActive(member, "/kaizen/chat/channels");
            order.verify(validator).ensureOrganizationIsActive(member, "/kaizen/chat/channels");
            order.verify(validator).ensureAppAccess(member, Fixture.class.getPackageName(), "/kaizen/chat/channels");
            assertSame(user, request.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER));
            assertSame(member, request.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_ORGANIZATION_MEMBER));
        }

        @Test
        void preHandle_pathVariableOrganization_isPassedToTheValidator() throws Exception {
            passingChain(OrganizationMemberRole.ADMIN);
            request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                    Map.of("organizationId", organizationId.toString()));

            interceptor.preHandle(request, response, handler("adminOnly"));

            verify(validator).ensureOrganizationIdIsValid(organizationId.toString(), null, "/kaizen/chat/channels");
        }

        @Test
        void preHandle_userNotProvisioned_stopsBeforeEveryLaterStep() throws Exception {
            when(validator.ensureUserIsProvisioned(SUBJECT, "/kaizen/chat/channels"))
                    .thenThrow(new ForbiddenException());

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> interceptor.preHandle(request, response, handler("adminOnly")));

            assertEquals("GEN_SEC_002", exception.getErrorCode());
            verify(validator, never()).ensureUserIsActive(any(), anyString());
            verify(validator, never()).ensureOrganizationIdIsValid(any(), any(), anyString());
            verify(validator, never()).ensureUserIsMember(any(), any(), anyString());
        }

        @Test
        void preHandle_userNotActive_stopsBeforeTheOrganizationSteps() throws Exception {
            when(validator.ensureUserIsProvisioned(SUBJECT, "/kaizen/chat/channels")).thenReturn(user);
            doThrow(new ForbiddenException()).when(validator).ensureUserIsActive(user, "/kaizen/chat/channels");

            assertThrows(ForbiddenException.class, () -> interceptor.preHandle(request, response, handler("adminOnly")));

            verify(validator, never()).ensureOrganizationIdIsValid(any(), any(), anyString());
            verify(validator, never()).ensureUserIsMember(any(), any(), anyString());
        }

        @Test
        void preHandle_organizationIdInvalid_stopsBeforeTheMembershipLookup() throws Exception {
            when(validator.ensureUserIsProvisioned(SUBJECT, "/kaizen/chat/channels")).thenReturn(user);
            when(validator.ensureOrganizationIdIsValid(any(), any(), anyString()))
                    .thenThrow(new BadRequestException(ExceptionDomainOrganizationError.API_ORGANIZATION_ID_MISMATCH));

            BadRequestException exception = assertThrows(BadRequestException.class,
                    () -> interceptor.preHandle(request, response, handler("adminOnly")));

            assertEquals("ORG_API_002", exception.getErrorCode());
            verify(validator, never()).ensureUserIsMember(any(), any(), anyString());
        }

        @Test
        void preHandle_notAMember_stopsBeforeTheMembershipStatusAndRole() throws Exception {
            when(validator.ensureUserIsProvisioned(SUBJECT, "/kaizen/chat/channels")).thenReturn(user);
            when(validator.ensureOrganizationIdIsValid(any(), any(), anyString())).thenReturn(organizationId);
            when(validator.ensureUserIsMember(user.getId(), organizationId, "/kaizen/chat/channels"))
                    .thenThrow(new ForbiddenException(ExceptionDomainOrganizationError.SEC_USER_NOT_MEMBER));

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> interceptor.preHandle(request, response, handler("adminOnly")));

            assertEquals("ORG_SEC_001", exception.getErrorCode());
            verify(validator, never()).ensureMembershipIsActive(any(), anyString());
            verify(validator, never()).ensureOrganizationIsActive(any(), anyString());
            assertNull(request.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_ORGANIZATION_MEMBER));
        }

        @Test
        void preHandle_membershipDisabled_stopsBeforeTheOrganizationStatus() throws Exception {
            OrganizationMember member = passingChain(OrganizationMemberRole.OWNER);
            doThrow(new ForbiddenException(ExceptionDomainOrganizationError.SEC_USER_NOT_MEMBER))
                    .when(validator).ensureMembershipIsActive(member, "/kaizen/chat/channels");

            assertThrows(ForbiddenException.class, () -> interceptor.preHandle(request, response, handler("adminOnly")));

            verify(validator, never()).ensureOrganizationIsActive(any(), anyString());
        }

        @Test
        void preHandle_organizationDisabled_throwsAndStoresNothing() throws Exception {
            OrganizationMember member = passingChain(OrganizationMemberRole.OWNER);
            doThrow(new ForbiddenException(ExceptionDomainOrganizationError.SEC_ORGANIZATION_DISABLED))
                    .when(validator).ensureOrganizationIsActive(member, "/kaizen/chat/channels");

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> interceptor.preHandle(request, response, handler("adminOnly")));

            assertEquals("ORG_SEC_005", exception.getErrorCode());
            assertNull(request.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER));
            assertNull(request.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_ORGANIZATION_MEMBER));
        }
    }

    @Nested
    class RoleLevel {

        @ParameterizedTest
        @EnumSource(value = OrganizationMemberRole.class, names = {"OWNER", "ADMIN"})
        void preHandle_roleAtLeastAdmin_passes(OrganizationMemberRole role) throws Exception {
            passingChain(role);

            assertTrue(interceptor.preHandle(request, response, handler("adminOnly")));
        }

        @ParameterizedTest
        @EnumSource(value = OrganizationMemberRole.class, names = {"MEMBER", "GUEST"})
        void preHandle_roleBelowAdmin_throwsInsufficientPermissionsAndStoresNothing(OrganizationMemberRole role)
                throws Exception {
            passingChain(role);

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> interceptor.preHandle(request, response, handler("adminOnly")));

            assertEquals("ORG_SEC_003", exception.getErrorCode());
            assertEquals(organizationId, exception.getErrorValues().get(OrganizationConstant.FIELD_ORGANIZATION_ID));
            assertNull(request.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER));
        }

        @ParameterizedTest
        @EnumSource(OrganizationMemberRole.class)
        void preHandle_guestLevel_everyRolePasses(OrganizationMemberRole role) throws Exception {
            passingChain(role);

            assertTrue(interceptor.preHandle(request, response, handler("guestOk")));
        }
    }

    @Nested
    class AppAccess {

        @Test
        void preHandle_roleTooLow_isRefusedForTheRoleAndTheAppIsNotChecked() throws Exception {
            passingChain(OrganizationMemberRole.MEMBER);

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> interceptor.preHandle(request, response, handler("adminOnly")));

            assertEquals("ORG_SEC_003", exception.getErrorCode());
            verify(validator, never()).ensureAppAccess(any(), anyString(), anyString());
        }

        @Test
        void preHandle_appAccessRefused_throwsAndStoresNothing() throws Exception {
            OrganizationMember member = passingChain(OrganizationMemberRole.ADMIN);
            doThrow(new ForbiddenException()).when(validator)
                    .ensureAppAccess(member, Fixture.class.getPackageName(), "/kaizen/chat/channels");

            assertThrows(ForbiddenException.class,
                    () -> interceptor.preHandle(request, response, handler("adminOnly")));

            assertNull(request.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER));
            assertNull(request.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_ORGANIZATION_MEMBER));
        }
    }

    @Nested
    class RequestAttributeReuse {

        @Test
        void preHandle_storedMembershipOfTheSameOrganization_skipsEveryOtherCheck() throws Exception {
            OrganizationMember stored = member(OrganizationMemberRole.MEMBER);
            request.setAttribute(SecurityConstant.REQUEST_ATTRIBUTE_ORGANIZATION_MEMBER, stored);
            request.addHeader("X-Organization-ID", organizationId.toString());
            when(validator.ensureOrganizationIdIsValid(null, organizationId.toString(), "/kaizen/chat/channels"))
                    .thenReturn(organizationId);

            assertTrue(interceptor.preHandle(request, response, handler("adminOnly")));

            verify(validator, never()).ensureUserIsProvisioned(any(), anyString());
            verify(validator, never()).ensureUserIsMember(any(), any(), anyString());
        }

        @Test
        void preHandle_storedMembershipOfAnotherOrganization_runsTheFullChain() throws Exception {
            UUID otherOrganization = UUID.randomUUID();
            OrganizationMember stored = OrganizationMember.builder()
                    .organization(Organization.builder().id(otherOrganization).build())
                    .user(user)
                    .role(OrganizationMemberRole.OWNER)
                    .build();
            request.setAttribute(SecurityConstant.REQUEST_ATTRIBUTE_ORGANIZATION_MEMBER, stored);
            OrganizationMember fresh = passingChain(OrganizationMemberRole.GUEST);

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> interceptor.preHandle(request, response, handler("adminOnly")));

            assertEquals("ORG_SEC_003", exception.getErrorCode());
            verify(validator).ensureUserIsProvisioned(SUBJECT, "/kaizen/chat/channels");
            verify(validator).ensureMembershipIsActive(fresh, "/kaizen/chat/channels");
        }

        @Test
        void preHandle_storedMembershipButPathAndHeaderDiffer_stillRefuses() throws Exception {
            OrganizationMember stored = member(OrganizationMemberRole.OWNER);
            request.setAttribute(SecurityConstant.REQUEST_ATTRIBUTE_ORGANIZATION_MEMBER, stored);
            when(validator.ensureOrganizationIdIsValid(any(), any(), anyString()))
                    .thenThrow(new BadRequestException(ExceptionDomainOrganizationError.API_ORGANIZATION_ID_MISMATCH));

            BadRequestException exception = assertThrows(BadRequestException.class,
                    () -> interceptor.preHandle(request, response, handler("adminOnly")));

            assertEquals("ORG_API_002", exception.getErrorCode());
        }
    }
}
