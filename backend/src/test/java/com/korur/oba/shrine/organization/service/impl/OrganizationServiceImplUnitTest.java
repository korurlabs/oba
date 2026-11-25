package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.mockito.InOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
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
import com.korur.oba.shrine.access.service.OrganizationGroupService;
import com.korur.oba.shrine.helper.AnonymizationHelper;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserService;
import com.korur.oba.shrine.invitation.service.InvitationService;
import com.korur.oba.shrine.organization.dto.request.OrganizationCreateRequest;
import com.korur.oba.shrine.organization.dto.request.OrganizationUpdateRequest;
import com.korur.oba.shrine.organization.dto.response.OrganizationDetailResponse;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;
import com.korur.oba.shrine.organization.event.OrganizationCreatedEvent;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.mapper.OrganizationMapper;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.shrine.organization.repository.OrganizationRepository;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplUnitTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private UserService userService;

    @Mock
    private InvitationService invitationService;

    @Mock
    private OrganizationMapper organizationMapper;


    @Mock
    private AnonymizationHelper anonymizationHelper;

    @Mock
    private OrganizationValidator organizationValidator;

    @Mock
    private OrganizationGroupService organizationGroupService;

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private OrganizationServiceImpl organizationServiceImpl;

    // ── Shared helpers ──────────────────────────────────────────────────────

    private User stubUser() {
        User user = User.builder().build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private Organization stubOrganization(UUID organizationId) {
        Organization organization = Organization.builder()
                .name("Acme")
                .status(OrganizationStatus.ACTIVE)
                .build();
        organization.setId(organizationId);
        return organization;
    }

    private OrganizationDetailResponse stubDetailResponse(UUID organizationId) {
        return new OrganizationDetailResponse(
                organizationId, null, null, "Acme", "desc", null, null, null, null, OrganizationStatus.ACTIVE);
    }

    private void stubAuditLogMapper() {
        when(auditLogMapper.toRequest(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditLogRequest(null, null, null, null, null, null, null));
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Nested
    class CreateOrganization {

        @Test
        void validRequest_savesOrganizationWithDescriptionAndNoCompanyName() {
            // Arrange
            User user = stubUser();
            UUID savedId = UUID.randomUUID();

            OrganizationCreateRequest request = new OrganizationCreateRequest(
                    "Acme", "We build things", "Tech", "1-10", "NLD", "https://acme.io");

            Organization savedOrg = Organization.builder()
                    .name("Acme")
                    .description("We build things")
                    .industry("Tech")
                    .companySize("1-10")
                    .country("NLD")
                    .website("https://acme.io")
                    .status(OrganizationStatus.ACTIVE)
                    .build();
            savedOrg.setId(savedId);

            OrganizationDetailResponse expectedResponse = stubDetailResponse(savedId);

            when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);
            when(organizationMapper.toDetailResponse(savedOrg)).thenReturn(expectedResponse);

            // Act
            OrganizationDetailResponse result = organizationServiceImpl.createOrganization(user, request);

            // Assert
            assertNotNull(result);
            assertEquals(savedId, result.id());

            // Verify the saved entity carries description (not companyName).
            ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
            verify(organizationRepository).save(orgCaptor.capture());
            Organization captured = orgCaptor.getValue();
            assertEquals("Acme", captured.getName());
            assertEquals("We build things", captured.getDescription());
            assertEquals(OrganizationStatus.ACTIVE, captured.getStatus());
        }

        @Test
        void validRequest_savesOwnerMembership() {
            // Arrange
            User user = stubUser();

            OrganizationCreateRequest request = new OrganizationCreateRequest(
                    "Acme", null, null, null, null, null);

            Organization savedOrg = stubOrganization(UUID.randomUUID());

            when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);
            when(organizationMapper.toDetailResponse(savedOrg)).thenReturn(stubDetailResponse(savedOrg.getId()));

            // Act
            organizationServiceImpl.createOrganization(user, request);

            // Assert — an OWNER membership was persisted.
            ArgumentCaptor<com.korur.oba.shrine.organization.entity.OrganizationMember> memberCaptor =
                    ArgumentCaptor.forClass(com.korur.oba.shrine.organization.entity.OrganizationMember.class);
            verify(organizationMemberRepository).save(memberCaptor.capture());
            assertEquals(OrganizationMemberRole.OWNER, memberCaptor.getValue().getRole());
            assertEquals(user, memberCaptor.getValue().getUser());
        }

        @Test
        void validRequest_publishesOrganizationCreatedEventOnce() {
            // Arrange
            User user = stubUser();

            OrganizationCreateRequest request = new OrganizationCreateRequest(
                    "Acme", null, null, null, null, null);

            Organization savedOrg = stubOrganization(UUID.randomUUID());

            when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);
            when(organizationMapper.toDetailResponse(savedOrg)).thenReturn(stubDetailResponse(savedOrg.getId()));

            // Act
            organizationServiceImpl.createOrganization(user, request);

            // Assert
            verify(applicationEventPublisher).publishEvent(new OrganizationCreatedEvent(savedOrg.getId()));
        }

        @Test
        void validRequest_createsEveryoneGroupBeforePublishingTheEvent() {
            // Arrange
            User user = stubUser();
            OrganizationCreateRequest request = new OrganizationCreateRequest(
                    "Acme", null, null, null, null, null);
            Organization savedOrg = stubOrganization(UUID.randomUUID());
            when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);
            when(organizationMapper.toDetailResponse(savedOrg)).thenReturn(stubDetailResponse(savedOrg.getId()));

            // Act
            organizationServiceImpl.createOrganization(user, request);

            // Assert
            InOrder order = inOrder(organizationGroupService, applicationEventPublisher);
            order.verify(organizationGroupService).createEveryoneGroup(savedOrg);
            order.verify(applicationEventPublisher).publishEvent(new OrganizationCreatedEvent(savedOrg.getId()));
        }

        @Test
        void requestWithNullDescription_savesOrganizationWithNullDescription() {
            // Arrange
            User user = stubUser();

            OrganizationCreateRequest request = new OrganizationCreateRequest(
                    "Acme", null, null, null, null, null);

            Organization savedOrg = Organization.builder()
                    .name("Acme")
                    .description(null)
                    .status(OrganizationStatus.ACTIVE)
                    .build();
            savedOrg.setId(UUID.randomUUID());

            when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);
            when(organizationMapper.toDetailResponse(savedOrg)).thenReturn(stubDetailResponse(savedOrg.getId()));

            // Act
            organizationServiceImpl.createOrganization(user, request);

            // Assert
            ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
            verify(organizationRepository).save(orgCaptor.capture());
            assertNull(orgCaptor.getValue().getDescription());
        }
    }

    @Nested
    class UpdateOrganization {

        @Test
        void adminAccess_updatesAndReturnsDetailResponse() {
            // Arrange
            User user = stubUser();
            UUID organizationId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            OrganizationUpdateRequest request = new OrganizationUpdateRequest(
                    "Updated Acme", "New desc", "Finance", "50-100", "DEU", "https://updated.io");
            OrganizationDetailResponse expectedResponse = stubDetailResponse(organizationId);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationRepository.save(organization)).thenReturn(organization);
            when(organizationMapper.toDetailResponse(organization)).thenReturn(expectedResponse);

            // Act
            OrganizationDetailResponse result = organizationServiceImpl.updateOrganization(user, organizationId, request);

            // Assert
            assertNotNull(result);
            verify(organizationMapper).updateEntity(organization, request);
            verify(organizationRepository).save(organization);
        }

        @Test
        void organizationNotFound_throwsNotFoundException() {
            // Arrange
            User user = stubUser();
            UUID organizationId = UUID.randomUUID();

            OrganizationUpdateRequest request = new OrganizationUpdateRequest(
                    "Updated", null, null, null, null, null);

            when(organizationValidator.ensureOrganizationExists(organizationId))
                    .thenThrow(new NotFoundException(ExceptionDomainOrganizationError.NOT_ORGANIZATION_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    organizationServiceImpl.updateOrganization(user, organizationId, request));
        }
    }

    @Nested
    class UpdateOrganizationStatus_Disable {

        @Test
        void ownerAccess_setsStatusToDisabled() {
            // Arrange
            User user = stubUser();
            UUID organizationId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationRepository.save(organization)).thenReturn(organization);
            stubAuditLogMapper();

            // Act
            organizationServiceImpl.updateOrganizationStatus(user, organizationId, OrganizationStatus.DISABLED);

            // Assert
            assertEquals(OrganizationStatus.DISABLED, organization.getStatus());
            verify(organizationValidator).ensureOrganizationExists(organizationId);
            verify(organizationValidator).ensureUserIsOwner(organizationId, user.getId());
            verify(organizationRepository).save(organization);
            verify(auditService).log(any(AuditLogRequest.class));
            verify(applicationEventPublisher).publishEvent(AccessRevokedEvent.organization(organizationId));
        }

        @Test
        void alreadyDisabled_throwsConflictException() {
            // Arrange
            User user = stubUser();
            UUID organizationId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            organization.setStatus(OrganizationStatus.DISABLED);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            doThrow(new ConflictException()).when(organizationValidator).ensureOrganizationIsActive(organization);

            // Act & Assert
            assertThrows(ConflictException.class, () ->
                    organizationServiceImpl.updateOrganizationStatus(user, organizationId, OrganizationStatus.DISABLED));
        }

        @Test
        void insufficientRole_throwsForbiddenException() {
            // Arrange
            User user = stubUser();
            UUID organizationId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            doThrow(new ForbiddenException(
                    ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS,
                    java.util.Map.of())).when(organizationValidator).ensureUserIsOwner(organizationId, user.getId());

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    organizationServiceImpl.updateOrganizationStatus(user, organizationId, OrganizationStatus.DISABLED));
        }

        @Test
        void organizationNotFound_throwsNotFoundException() {
            // Arrange
            User user = stubUser();
            UUID organizationId = UUID.randomUUID();

            when(organizationValidator.ensureOrganizationExists(organizationId))
                    .thenThrow(new NotFoundException(ExceptionDomainOrganizationError.NOT_ORGANIZATION_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    organizationServiceImpl.updateOrganizationStatus(user, organizationId, OrganizationStatus.DISABLED));
        }
    }

    @Nested
    class UpdateOrganizationStatus_Enable {

        @Test
        void disabledOrg_setsStatusToActive() {
            // Arrange
            User user = stubUser();
            UUID organizationId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            organization.setStatus(OrganizationStatus.DISABLED);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationRepository.save(organization)).thenReturn(organization);
            stubAuditLogMapper();

            // Act
            organizationServiceImpl.updateOrganizationStatus(user, organizationId, OrganizationStatus.ACTIVE);

            // Assert
            assertEquals(OrganizationStatus.ACTIVE, organization.getStatus());
            verify(organizationValidator).ensureUserIsOwner(organizationId, user.getId());
            verify(organizationRepository).save(organization);
            verify(auditService).log(any(AuditLogRequest.class));
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        void activeOrg_throwsConflictException() {
            // Arrange
            User user = stubUser();
            UUID organizationId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            doThrow(new ConflictException()).when(organizationValidator).ensureOrganizationIsDisabled(organization);

            // Act & Assert
            assertThrows(ConflictException.class, () ->
                    organizationServiceImpl.updateOrganizationStatus(user, organizationId, OrganizationStatus.ACTIVE));
        }
    }

    @Nested
    class DeleteOrganization {

        @Test
        void ownerAccess_anonymizesAndSoftDeletes() {
            // Arrange
            User user = stubUser();
            UUID organizationId = UUID.randomUUID();

            Organization organization = stubOrganization(organizationId);
            organization.setDescription("Original description");
            organization.setWebsite("https://example.com");

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationRepository.save(organization)).thenReturn(organization);
            stubAuditLogMapper();

            // Act
            organizationServiceImpl.deleteOrganization(user, organizationId);

            // Assert
            verify(organizationValidator).ensureOrganizationExists(organizationId);
            verify(anonymizationHelper).anonymizeOrganization(organization);
            verify(organizationRepository).save(organization);
            verify(organizationRepository).delete(organization);
            verify(auditService).log(any(AuditLogRequest.class));
            verify(applicationEventPublisher).publishEvent(AccessRevokedEvent.organization(organizationId));
        }

        @Test
        void withMembers_appliesDeletionToEachMemberBeforeSoftDelete() {
            // Arrange
            User user = stubUser();
            UUID organizationId = UUID.randomUUID();
            Organization organization = stubOrganization(organizationId);
            User firstMember = stubUser();
            User secondMember = stubUser();
            organization.getMembers().add(OrganizationMember.builder().organization(organization).user(firstMember).build());
            organization.getMembers().add(OrganizationMember.builder().organization(organization).user(secondMember).build());

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationRepository.save(organization)).thenReturn(organization);
            stubAuditLogMapper();

            // Act
            organizationServiceImpl.deleteOrganization(user, organizationId);

            // Assert — the users are updated through the identity service before the organization is deleted.
            InOrder inOrder = inOrder(userService, organizationRepository);
            inOrder.verify(userService).applyOrganizationDeletion(firstMember.getId(), organizationId);
            inOrder.verify(userService).applyOrganizationDeletion(secondMember.getId(), organizationId);
            inOrder.verify(organizationRepository).delete(organization);
        }

        @Test
        void withPendingInvitations_revokesThemBeforeSoftDelete() {
            // Arrange
            User user = stubUser();
            UUID organizationId = UUID.randomUUID();
            Organization organization = stubOrganization(organizationId);

            when(organizationValidator.ensureOrganizationExists(organizationId)).thenReturn(organization);
            when(organizationRepository.save(organization)).thenReturn(organization);
            stubAuditLogMapper();

            // Act
            organizationServiceImpl.deleteOrganization(user, organizationId);

            // Assert: the invitations are revoked through the invitation service before the organization is deleted.
            InOrder inOrder = inOrder(invitationService, organizationRepository);
            inOrder.verify(invitationService).revokePendingInvitationsOfOrganization(organizationId);
            inOrder.verify(organizationRepository).delete(organization);
        }
    }
}
