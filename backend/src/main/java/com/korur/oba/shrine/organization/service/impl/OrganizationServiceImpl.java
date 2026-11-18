package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.service.OrganizationGroupService;
import com.korur.oba.shrine.helper.AnonymizationHelper;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserService;
import com.korur.oba.shrine.organization.constant.service.OrganizationServiceLogConstant;
import com.korur.oba.shrine.organization.dto.request.OrganizationCreateRequest;
import com.korur.oba.shrine.organization.dto.request.OrganizationUpdateRequest;
import com.korur.oba.shrine.organization.dto.response.OrganizationDetailResponse;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;
import com.korur.oba.shrine.organization.event.OrganizationCreatedEvent;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.mapper.OrganizationMapper;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.shrine.organization.repository.OrganizationRepository;
import com.korur.oba.shrine.organization.service.OrganizationService;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

/**
 * Implements write operations for organization lifecycle management.
 *
 * Disabling or deleting an organization ends every member's access to it, so both publish
 * {@link AccessRevokedEvent} for the whole organization, and state built on that access, such as open
 * realtime sessions, is torn down once the change commits.
 */
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationServiceImpl.class);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserService userService;
    private final OrganizationMapper organizationMapper;
    private final AnonymizationHelper anonymizationHelper;
    private final OrganizationValidator organizationValidator;
    private final OrganizationGroupService organizationGroupService;
    private final AuditLogMapper auditLogMapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LoggingService loggingService;

    /**
     * Creates a new organization and registers the current user as owner.
     */
    @Override
    @Transactional
    public OrganizationDetailResponse createOrganization(User user, OrganizationCreateRequest request) {

        // Build the organization using request fields.
        Organization organization = Organization.builder()
                .name(request.name())
                .description(request.description())
                .industry(request.industry())
                .companySize(request.companySize())
                .country(request.country())
                .website(request.website())
                .status(OrganizationStatus.ACTIVE)
                .build();

        // Persist the organization before creating membership.
        organization = organizationRepository.save(organization);

        // Create the owner membership so the creator has full access.
        OrganizationMember member = OrganizationMember.builder()
                .organization(organization)
                .user(user)
                .role(OrganizationMemberRole.OWNER)
                .status(OrganizationMemberStatus.ACTIVE)
                .build();

        // Persist the membership record.
        organizationMemberRepository.save(member);

        // Create the "Everyone" group in this transaction, so a new organization always has a group of every member.
        organizationGroupService.createEveryoneGroup(organization);

        // Let listeners set up what every organization starts with, inside this transaction.
        applicationEventPublisher.publishEvent(new OrganizationCreatedEvent(organization.getId()));

        // Log the creation event for auditing.
        loggingService.info(
                LOG_PREFIX,
                LogLayer.SERVICE,
                OrganizationServiceLogConstant.ORG_CREATED,
                organization.getId());

        // Return the detail response for the created organization.
        return organizationMapper.toDetailResponse(organization);
    }

    /**
     * Updates organization metadata. The caller's ADMIN role is checked by
     * {@link com.korur.oba.dojo.security.annotation.RequiredUserAccess} before this runs.
     */
    @Override
    @Transactional
    public OrganizationDetailResponse updateOrganization(User user, UUID organizationId, OrganizationUpdateRequest request) {
        Organization organization = organizationValidator.ensureOrganizationExists(organizationId);

        // Apply the full update: every request field is written, and a null optional field clears it.
        organizationMapper.updateEntity(organization, request);
        // Persist the updated organization.
        organization = organizationRepository.save(organization);

        // Log the update event for auditing.
        loggingService.info(
                LOG_PREFIX,
                LogLayer.SERVICE,
                OrganizationServiceLogConstant.ORG_UPDATED,
                organization.getId());

        // Return the updated organization detail response.
        return organizationMapper.toDetailResponse(organization);
    }

    /**
     * Updates an organization's lifecycle status (enable/disable).
     */
    @Override
    @Transactional
    public void updateOrganizationStatus(User user, UUID organizationId, OrganizationStatus newStatus) {
        // This path is exempt from RequiredUserAccess, which refuses a disabled organization,
        // because only here can an owner enable it again; the owner check is therefore made here.
        Organization organization = organizationValidator.ensureOrganizationExists(organizationId);
        organizationValidator.ensureUserIsOwner(organizationId, user.getId());

        Map<String, Object> oldValues = Map.of("status", organization.getStatus().name());

        if (newStatus == OrganizationStatus.DISABLED) {
            // Guard: organization must be ACTIVE to disable.
            organizationValidator.ensureOrganizationIsActive(organization);

            organization.setStatus(OrganizationStatus.DISABLED);
            organizationRepository.save(organization);
            applicationEventPublisher.publishEvent(AccessRevokedEvent.organization(organizationId));

            auditService.log(auditLogMapper.toRequest(organizationId, user.getId(),
                    OrganizationServiceLogConstant.AUDIT_ACTION_ORG_DISABLED,
                    OrganizationServiceLogConstant.AUDIT_ENTITY_TYPE, organizationId, oldValues,
                    Map.of("status", OrganizationStatus.DISABLED.name())));

            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    OrganizationServiceLogConstant.ORG_DISABLED, organizationId);

        } else if (newStatus == OrganizationStatus.ACTIVE) {
            // Guard: organization must be DISABLED to enable.
            organizationValidator.ensureOrganizationIsDisabled(organization);

            organization.setStatus(OrganizationStatus.ACTIVE);
            organizationRepository.save(organization);

            auditService.log(auditLogMapper.toRequest(organizationId, user.getId(),
                    OrganizationServiceLogConstant.AUDIT_ACTION_ORG_ENABLED,
                    OrganizationServiceLogConstant.AUDIT_ENTITY_TYPE, organizationId, oldValues,
                    Map.of("status", OrganizationStatus.ACTIVE.name())));

            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    OrganizationServiceLogConstant.ORG_ENABLED, organizationId);

        } else {
            throw new ValidationException(ExceptionDomainOrganizationError.VAL_INVALID_ORGANIZATION_STATUS,
                    Map.of("status", newStatus.name()));
        }
    }

    /**
     * Permanently deletes an organization by anonymizing and soft-deleting.
     * JPA cascades soft-delete to members.
     */
    @Override
    @Transactional
    public void deleteOrganization(User user, UUID organizationId) {
        // The caller's OWNER role in an enabled organization is checked by RequiredUserAccess.
        Organization organization = organizationValidator.ensureOrganizationExists(organizationId);

        Map<String, Object> oldValues = Map.of(
                "name", organization.getName(),
                "status", organization.getStatus().name());

        // Move each member's default organization away and revert members left without an organization.
        // The members' users are managed in this transaction: a database-only update would leave them
        // referencing the deleted organization, and the flush would then fail.
        for (OrganizationMember member : organization.getMembers()) {
            userService.applyOrganizationDeletion(member.getUser().getId(), organizationId);
        }

        // Anonymize organization data before soft-deleting.
        anonymizationHelper.anonymizeOrganization(organization);
        organizationRepository.save(organization);

        // Soft-delete the organization. JPA cascades to members.
        organizationRepository.delete(organization);
        applicationEventPublisher.publishEvent(AccessRevokedEvent.organization(organizationId));

        // Record audit trail.
        auditService.log(auditLogMapper.toRequest(organizationId, user.getId(),
                OrganizationServiceLogConstant.AUDIT_ACTION_ORG_DELETED,
                OrganizationServiceLogConstant.AUDIT_ENTITY_TYPE, organizationId, oldValues,
                Map.of("softDeleted", true)));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                OrganizationServiceLogConstant.ORG_SOFT_DELETED, organizationId);
    }
}
