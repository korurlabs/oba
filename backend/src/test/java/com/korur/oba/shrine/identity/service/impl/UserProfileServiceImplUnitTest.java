package com.korur.oba.shrine.identity.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.dto.request.AuditLogRequest;
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.dto.request.UserSelfUpdateRequest;
import com.korur.oba.shrine.identity.dto.response.UserDetailResponse;
import com.korur.oba.shrine.identity.dto.response.UserSyncResult;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.shrine.identity.mapper.UserMapper;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.identity.validator.UserValidator;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplUnitTest {

    private static final String KEYCLOAK_ID = "kc-subject";
    private static final String EMAIL = "person@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserValidator userValidator;

    @Mock
    private UserMapper userMapper;

    @Mock
    private OrganizationQueryService organizationQueryService;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private UserProfileServiceImpl userProfileServiceImpl;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String sub, String email) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", sub);
        if (email != null) {
            builder.claim("email", email);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }

    @Nested
    class SyncUserFromJwt {

        @Test
        void deletedKeycloakId_throwsForbiddenAndSavesNothing() {
            // Arrange
            authenticate(KEYCLOAK_ID, EMAIL);
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
            doThrow(new ForbiddenException(ExceptionDomainIdentityError.SEC_USER_DELETED))
                    .when(userValidator).ensureKeycloakIdNotDeleted(KEYCLOAK_ID);

            // Act
            ForbiddenException exception = assertThrows(ForbiddenException.class,
                    () -> userProfileServiceImpl.syncUserFromJwt());

            // Assert
            assertEquals("IDN_SEC_001", exception.getErrorCode());
            verify(userRepository, never()).save(any());
            verify(userRepository, never()).findByEmailIgnoreCase(anyString());
            verify(userValidator, never()).ensureMailIsUnique(anyString());
        }

        @Test
        void knownKeycloakId_returnsExistingUserNotCreated() {
            // Arrange
            authenticate(KEYCLOAK_ID, EMAIL);
            User existing = User.builder()
                    .keycloakId(KEYCLOAK_ID)
                    .email(EMAIL)
                    .status(UserStatus.DISABLED)
                    .build();
            UserDetailResponse response = mock(UserDetailResponse.class);
            Map<UUID, List<App>> apps = Map.of(UUID.randomUUID(), List.of(App.KAIZEN));
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));
            when(appAccessQueryService.listAccessibleAppsByOrganization(existing)).thenReturn(apps);
            when(userMapper.toDetailResponse(existing, apps)).thenReturn(response);

            // Act
            UserSyncResult result = userProfileServiceImpl.syncUserFromJwt();

            // Assert
            assertSame(response, result.user());
            assertFalse(result.created());
            verify(userValidator, never()).ensureKeycloakIdNotDeleted(anyString());
            verify(userRepository, never()).save(any());
        }

        @Test
        void unknownUser_createsPendingOnboardingUser() {
            // Arrange
            authenticate(KEYCLOAK_ID, EMAIL);
            UserDetailResponse response = mock(UserDetailResponse.class);
            when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
            when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
            when(userMapper.toDetailResponse(any(User.class), any())).thenReturn(response);

            // Act
            UserSyncResult result = userProfileServiceImpl.syncUserFromJwt();

            // Assert
            assertTrue(result.created());
            assertSame(response, result.user());
            verify(userValidator).ensureKeycloakIdNotDeleted(KEYCLOAK_ID);
            verify(userValidator).ensureMailIsUnique(EMAIL);
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertEquals(UserStatus.PENDING_ONBOARDING, captor.getValue().getStatus());
            assertEquals(KEYCLOAK_ID, captor.getValue().getKeycloakId());
            assertEquals(EMAIL, captor.getValue().getEmail());
        }
    }

    private User activeUser(UUID userId) {
        User user = User.builder()
                .email(EMAIL)
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(userId);
        return user;
    }

    @Nested
    class UpdateProfile {

        @Test
        void selfRequest_usesSelfOverloadOfMapperAndSaves() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId);
            UserSelfUpdateRequest request = new UserSelfUpdateRequest(null, null, null, null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);

            // Act
            userProfileServiceImpl.updateProfile(userId, request);

            // Assert
            verify(userMapper).updateEntity(user, request);
            verify(userRepository).save(user);
        }

        @Test
        void unknownUser_throwsNotFoundAndSavesNothing() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> userProfileServiceImpl.updateProfile(userId,
                    new UserSelfUpdateRequest(null, null, null, null)));
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    class ClearDefaultOrganization {

        @Test
        void defaultOrganizationSet_clearsItAndSaves() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId);
            user.setDefaultOrganization(Organization.builder().id(UUID.randomUUID()).build());
            UserDetailResponse response = mock(UserDetailResponse.class);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);
            when(userMapper.toDetailResponse(eq(user), any())).thenReturn(response);

            // Act
            UserDetailResponse result = userProfileServiceImpl.clearDefaultOrganization(userId);

            // Assert
            assertNull(user.getDefaultOrganization());
            assertSame(response, result);
            verify(userRepository).save(user);
            verifyNoInteractions(organizationQueryService);
        }

        @Test
        void unknownUser_throwsNotFoundAndSavesNothing() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> userProfileServiceImpl.clearDefaultOrganization(userId));
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    class DisableOwnAccount {

        @Test
        void soleOwner_throwsConflictBeforeSave() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            doThrow(new ConflictException(ExceptionDomainOrganizationError.STA_SOLE_OWNER_CANNOT_LEAVE))
                    .when(organizationMemberQueryService).ensureNotSoleOwnerOfAnyOrganization(userId);

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> userProfileServiceImpl.disableOwnAccount(userId));

            // Assert
            assertEquals("ORG_STA_003", exception.getErrorCode());
            assertEquals(UserStatus.ACTIVE, user.getStatus());
            verify(userRepository, never()).save(any());
            verifyNoInteractions(auditService);
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        void coOwnedOrganizations_disablesAndAuditsAsAccountHolder() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(auditLogMapper.toRequest(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new AuditLogRequest(null, null, null, null, null, null, null));

            // Act
            userProfileServiceImpl.disableOwnAccount(userId);

            // Assert
            assertEquals(UserStatus.DISABLED, user.getStatus());
            verify(organizationMemberQueryService).ensureNotSoleOwnerOfAnyOrganization(userId);
            verify(userRepository).save(user);
            verify(auditLogMapper).toRequest(any(), eq(userId), any(), any(), eq(userId), any(), any());
            verify(auditService).log(any(AuditLogRequest.class));
        }

        @Test
        void coOwnedOrganizations_revokesAccessToEveryOrganizationOfTheUser() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(auditLogMapper.toRequest(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new AuditLogRequest(null, null, null, null, null, null, null));

            // Act
            userProfileServiceImpl.disableOwnAccount(userId);

            // Assert
            verify(applicationEventPublisher).publishEvent(AccessRevokedEvent.user(userId));
        }
    }
}
