package com.korur.oba.shrine.identity.validator.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.constant.UserConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.identity.validator.UserAuthorizationValidator;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthorizationValidatorUnitTest {

    private static final String PATH = "/kaizen/chat/channels";

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private UserAuthorizationValidator validator;

    private static OrganizationMember member(
            UUID organizationId, OrganizationMemberStatus memberStatus, OrganizationStatus organizationStatus) {
        Organization organization = Organization.builder().id(organizationId).status(organizationStatus).build();
        User user = User.builder().id(UUID.randomUUID()).build();
        return OrganizationMember.builder()
                .organization(organization)
                .user(user)
                .role(OrganizationMemberRole.MEMBER)
                .status(memberStatus)
                .build();
    }

    @Nested
    class EnsureUserIsProvisioned {

        @Test
        void knownKeycloakId_returnsUser() {
            User user = User.builder().id(UUID.randomUUID()).build();
            when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(user));

            User result = validator.ensureUserIsProvisioned("kc-1", PATH);

            assertSame(user, result);
        }

        @Test
        void unknownKeycloakId_throwsForbidden() {
            when(userRepository.findByKeycloakId("kc-unknown")).thenReturn(Optional.empty());

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> validator.ensureUserIsProvisioned("kc-unknown", PATH));

            assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
            assertEquals("GEN_SEC_002", exception.getErrorCode());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void blankSubject_throwsForbiddenWithoutLookup(String subject) {
            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> validator.ensureUserIsProvisioned(subject, PATH));

            assertEquals("GEN_SEC_002", exception.getErrorCode());
            verifyNoInteractions(userRepository);
        }
    }

    @Nested
    class EnsureUserIsActive {

        @Test
        void activeUser_passes() {
            User user = User.builder().id(UUID.randomUUID()).status(UserStatus.ACTIVE).build();

            assertDoesNotThrow(() -> validator.ensureUserIsActive(user, PATH));
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
        void notActiveUser_throwsForbiddenWithStatus(UserStatus status) {
            User user = User.builder().id(UUID.randomUUID()).status(status).build();

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> validator.ensureUserIsActive(user, PATH));

            assertEquals("GEN_SEC_002", exception.getErrorCode());
            assertEquals(status.name(), exception.getErrorValues().get(UserConstant.FIELD_USER_STATUS));
        }
    }

    @Nested
    class EnsureOrganizationIdIsValid {

        private final UUID id = UUID.randomUUID();

        @Test
        void pathOnly_returnsPathId() {
            assertEquals(id, validator.ensureOrganizationIdIsValid(id.toString(), null, PATH));
        }

        @Test
        void headerOnly_returnsHeaderId() {
            assertEquals(id, validator.ensureOrganizationIdIsValid(null, id.toString(), PATH));
        }

        @Test
        void pathAndHeaderEqual_returnsTheId() {
            assertEquals(id, validator.ensureOrganizationIdIsValid(id.toString(), id.toString(), PATH));
        }

        @Test
        void blankPathAndHeaderId_countsPathAsAbsent() {
            assertEquals(id, validator.ensureOrganizationIdIsValid(" ", id.toString(), PATH));
        }

        @Test
        void headerBlankAndPathId_countsHeaderAsAbsent() {
            assertEquals(id, validator.ensureOrganizationIdIsValid(id.toString(), "", PATH));
        }

        @ParameterizedTest
        @NullAndEmptySource
        void neitherPresent_throwsMissingHeader(String blank) {
            BadRequestException exception = assertThrows(BadRequestException.class,
                    () -> validator.ensureOrganizationIdIsValid(blank, blank, PATH));

            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
            assertEquals("GEN_API_007", exception.getErrorCode());
            assertEquals("X-Organization-ID", exception.getErrorValues().get(UserConstant.FIELD_HEADER));
        }

        @Test
        void pathNotUuid_throwsBadRequest() {
            BadRequestException exception = assertThrows(BadRequestException.class,
                    () -> validator.ensureOrganizationIdIsValid("not-a-uuid", id.toString(), PATH));

            assertEquals("GEN_API_001", exception.getErrorCode());
        }

        @Test
        void headerNotUuid_throwsInvalidOrganizationId() {
            BadRequestException exception = assertThrows(BadRequestException.class,
                    () -> validator.ensureOrganizationIdIsValid(null, "not-a-uuid", PATH));

            assertEquals("ORG_API_001", exception.getErrorCode());
        }

        @Test
        void pathAndHeaderDiffer_throwsMismatchWithPathId() {
            UUID other = UUID.randomUUID();

            BadRequestException exception = assertThrows(BadRequestException.class,
                    () -> validator.ensureOrganizationIdIsValid(id.toString(), other.toString(), PATH));

            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
            assertEquals("ORG_API_002", exception.getErrorCode());
            assertEquals(id, exception.getErrorValues().get(OrganizationConstant.FIELD_ORGANIZATION_ID));
        }

        @Test
        void invalidHeader_doesNotLogTheRawValue() {
            String raw = "secret-header-value";

            assertThrows(BadRequestException.class, () -> validator.ensureOrganizationIdIsValid(null, raw, PATH));

            ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR), any(String.class), params.capture());
            for (Object param : params.getValue()) {
                assertFalse(String.valueOf(param).contains(raw), "the raw header value reached the log");
            }
        }
    }

    @Nested
    class EnsureUserIsMember {

        private final UUID userId = UUID.randomUUID();
        private final UUID organizationId = UUID.randomUUID();

        @Test
        void membershipExists_returnsIt() {
            OrganizationMember found = member(organizationId, OrganizationMemberStatus.ACTIVE, OrganizationStatus.ACTIVE);
            when(organizationMemberQueryService.findMember(organizationId, userId)).thenReturn(Optional.of(found));

            assertSame(found, validator.ensureUserIsMember(userId, organizationId, PATH));
        }

        @Test
        void noMembership_throwsNotMember() {
            when(organizationMemberQueryService.findMember(organizationId, userId)).thenReturn(Optional.empty());

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> validator.ensureUserIsMember(userId, organizationId, PATH));

            assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
            assertEquals("ORG_SEC_001", exception.getErrorCode());
            assertEquals(organizationId, exception.getErrorValues().get(OrganizationConstant.FIELD_ORGANIZATION_ID));
        }
    }

    @Nested
    class EnsureMembershipIsActive {

        @Test
        void activeMembership_passes() {
            OrganizationMember member = member(UUID.randomUUID(), OrganizationMemberStatus.ACTIVE, OrganizationStatus.ACTIVE);

            assertDoesNotThrow(() -> validator.ensureMembershipIsActive(member, PATH));
        }

        @ParameterizedTest
        @EnumSource(value = OrganizationMemberStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
        void inactiveMembership_throwsNotMember(OrganizationMemberStatus status) {
            OrganizationMember member = member(UUID.randomUUID(), status, OrganizationStatus.ACTIVE);

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> validator.ensureMembershipIsActive(member, PATH));

            assertEquals("ORG_SEC_001", exception.getErrorCode());
        }
    }

    @Nested
    class EnsureOrganizationIsActive {

        @Test
        void activeOrganization_passes() {
            OrganizationMember member = member(UUID.randomUUID(), OrganizationMemberStatus.ACTIVE, OrganizationStatus.ACTIVE);

            assertDoesNotThrow(() -> validator.ensureOrganizationIsActive(member, PATH));
        }

        @ParameterizedTest
        @EnumSource(value = OrganizationStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
        void inactiveOrganization_throwsOrganizationDisabled(OrganizationStatus status) {
            UUID organizationId = UUID.randomUUID();
            OrganizationMember member = member(organizationId, OrganizationMemberStatus.ACTIVE, status);

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> validator.ensureOrganizationIsActive(member, PATH));

            assertEquals("ORG_SEC_005", exception.getErrorCode());
            assertEquals(organizationId, exception.getErrorValues().get(OrganizationConstant.FIELD_ORGANIZATION_ID));
        }
    }

    @Nested
    class EnsureAppAccess {

        private static final String KAIZEN_PACKAGE = "com.korur.oba.apps.kaizen.board.controller";

        @ParameterizedTest
        @ValueSource(strings = {
                "com.korur.oba.shrine.organization.controller",
                "com.korur.oba.connect.webhook",
                "com.korur.oba.dojo.health",
                "com.korur.oba.apps"})
        void packageOutsideTheApps_passesWithoutAnyCheck(String handlerPackage) {
            OrganizationMember member = member(UUID.randomUUID(), OrganizationMemberStatus.ACTIVE, OrganizationStatus.ACTIVE);

            assertDoesNotThrow(() -> validator.ensureAppAccess(member, handlerPackage, PATH));

            verifyNoInteractions(appAccessQueryService);
        }

        @Test
        void nullPackage_passesWithoutAnyCheck() {
            OrganizationMember member = member(UUID.randomUUID(), OrganizationMemberStatus.ACTIVE, OrganizationStatus.ACTIVE);

            assertDoesNotThrow(() -> validator.ensureAppAccess(member, null, PATH));

            verifyNoInteractions(appAccessQueryService);
        }

        @Test
        void unknownApp_throwsAppAccessDeniedWithoutAskingTheQueryService() {
            UUID organizationId = UUID.randomUUID();
            OrganizationMember member = member(organizationId, OrganizationMemberStatus.ACTIVE, OrganizationStatus.ACTIVE);

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> validator.ensureAppAccess(member, "com.korur.oba.apps.newapp.controller", PATH));

            assertEquals("APP_SEC_001", exception.getErrorCode());
            assertEquals(organizationId, exception.getErrorValues().get(OrganizationConstant.FIELD_ORGANIZATION_ID));
            verifyNoInteractions(appAccessQueryService);
        }

        @Test
        void grantMissing_throwsAppAccessDeniedNamingTheApp() {
            UUID organizationId = UUID.randomUUID();
            OrganizationMember member = member(organizationId, OrganizationMemberStatus.ACTIVE, OrganizationStatus.ACTIVE);
            when(appAccessQueryService.hasAppAccess(member, App.KAIZEN)).thenReturn(false);

            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> validator.ensureAppAccess(member, KAIZEN_PACKAGE, PATH));

            assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
            assertEquals("APP_SEC_001", exception.getErrorCode());
            assertEquals("KAIZEN", exception.getErrorValues().get(AccessConstant.FIELD_APP));
        }

        @Test
        void grantPresent_passes() {
            OrganizationMember member = member(UUID.randomUUID(), OrganizationMemberStatus.ACTIVE, OrganizationStatus.ACTIVE);
            when(appAccessQueryService.hasAppAccess(member, App.KAIZEN)).thenReturn(true);

            assertDoesNotThrow(() -> validator.ensureAppAccess(member, KAIZEN_PACKAGE, PATH));
        }

        @Test
        void packageWithoutSubpackage_resolvesTheAppFromTheWholeSegment() {
            OrganizationMember member = member(UUID.randomUUID(), OrganizationMemberStatus.ACTIVE, OrganizationStatus.ACTIVE);
            when(appAccessQueryService.hasAppAccess(member, App.KAYA)).thenReturn(true);

            assertDoesNotThrow(() -> validator.ensureAppAccess(member, "com.korur.oba.apps.kaya", PATH));

            verify(appAccessQueryService).hasAppAccess(member, App.KAYA);
        }
    }
}
