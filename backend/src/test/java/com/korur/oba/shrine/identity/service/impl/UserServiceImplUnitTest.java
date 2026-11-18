package com.korur.oba.shrine.identity.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.identity.validator.UserValidator;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

@ExtendWith(MockitoExtension.class)
class UserServiceImplUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationQueryService organizationQueryService;

    @Mock
    private UserValidator userValidator;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private UserServiceImpl userServiceImpl;

    // ── Shared helpers ──────────────────────────────────────────────────────

    private User stubUser(UUID userId) {
        User user = User.builder()
                .email("user@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(userId);
        return user;
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Nested
    class ApplyOrganizationDeletion {

        private Organization organization() {
            Organization organization = Organization.builder().name("Acme").build();
            organization.setId(UUID.randomUUID());
            return organization;
        }

        private void addMembership(User user, Organization organization, OrganizationMemberStatus status) {
            user.getOrganizationMemberships().add(OrganizationMember.builder()
                    .organization(organization).user(user).status(status).build());
        }

        @Test
        void defaultIsDeleted_movesDefaultToActiveOtherMembership() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Organization deleted = organization();
            Organization disabledOther = organization();
            Organization activeOther = organization();
            User user = stubUser(userId);
            user.setDefaultOrganization(deleted);
            addMembership(user, deleted, OrganizationMemberStatus.ACTIVE);
            addMembership(user, disabledOther, OrganizationMemberStatus.DISABLED);
            addMembership(user, activeOther, OrganizationMemberStatus.ACTIVE);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // Act
            userServiceImpl.applyOrganizationDeletion(userId, deleted.getId());

            // Assert
            assertSame(activeOther, user.getDefaultOrganization());
            assertEquals(UserStatus.ACTIVE, user.getStatus());
            verify(userRepository).save(user);
        }

        @Test
        void defaultIsDeletedAndNoOtherMembership_clearsDefaultAndRevertsToOnboarding() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Organization deleted = organization();
            User user = stubUser(userId);
            user.setDefaultOrganization(deleted);
            addMembership(user, deleted, OrganizationMemberStatus.ACTIVE);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // Act
            userServiceImpl.applyOrganizationDeletion(userId, deleted.getId());

            // Assert
            assertNull(user.getDefaultOrganization());
            assertEquals(UserStatus.PENDING_ONBOARDING, user.getStatus());
        }

        @Test
        void defaultIsDeletedAndOnlyDisabledOther_clearsDefaultAndKeepsStatus() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Organization deleted = organization();
            User user = stubUser(userId);
            user.setDefaultOrganization(deleted);
            addMembership(user, deleted, OrganizationMemberStatus.ACTIVE);
            addMembership(user, organization(), OrganizationMemberStatus.DISABLED);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // Act
            userServiceImpl.applyOrganizationDeletion(userId, deleted.getId());

            // Assert
            assertNull(user.getDefaultOrganization());
            assertEquals(UserStatus.ACTIVE, user.getStatus());
        }

        @Test
        void defaultIsAnotherOrganization_keepsDefault() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Organization deleted = organization();
            Organization other = organization();
            User user = stubUser(userId);
            user.setDefaultOrganization(other);
            addMembership(user, deleted, OrganizationMemberStatus.ACTIVE);
            addMembership(user, other, OrganizationMemberStatus.ACTIVE);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // Act
            userServiceImpl.applyOrganizationDeletion(userId, deleted.getId());

            // Assert
            assertSame(other, user.getDefaultOrganization());
            assertEquals(UserStatus.ACTIVE, user.getStatus());
        }

        @Test
        void unknownUser_throwsNotFound() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act and assert
            assertThrows(NotFoundException.class,
                    () -> userServiceImpl.applyOrganizationDeletion(userId, UUID.randomUUID()));
            verify(userRepository, never()).save(any());
        }
    }
}
