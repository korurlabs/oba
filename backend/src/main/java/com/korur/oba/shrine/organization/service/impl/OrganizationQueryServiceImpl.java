package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.response.OrganizationDetailResponse;
import com.korur.oba.shrine.organization.dto.response.OrganizationListResponse;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.mapper.OrganizationMapper;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.shrine.organization.repository.OrganizationRepository;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

/**
 * Implements read operations for organizations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationQueryServiceImpl implements OrganizationQueryService {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;
    private final OrganizationValidator organizationValidator;

    /**
     * Retrieves organization details by id.
     */
    @Override
    public OrganizationDetailResponse getOrganization(UUID organizationId) {
        // Ensure the organization exists before mapping.
        Organization organization = organizationValidator.ensureOrganizationExists(organizationId);
        // Map entity to detail response.
        return organizationMapper.toDetailResponse(organization);
    }

    /**
     * Lists organizations for the current user.
     */
    @Override
    public List<OrganizationListResponse> listOrganizations(User user) {
        // Load all memberships for the user.
        List<OrganizationMember> memberships = organizationMemberRepository.findByUser_Id(user.getId());
        // Map organizations from memberships into list responses.
        return memberships.stream()
                .map(OrganizationMember::getOrganization)
                .map(organizationMapper::toListResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the Organization entity by ID.
     * Used by other domains that need the entity for relational mappings.
     */
    @Override
    public Organization getOrganizationEntityById(UUID organizationId) {
        return organizationValidator.ensureOrganizationExists(organizationId);
    }

    @Override
    public Optional<OrganizationStatus> findOrganizationStatus(UUID organizationId) {
        return organizationRepository.findStatusById(organizationId);
    }

    /**
     * Retrieves all organization IDs.
     * Used by scheduled tasks that need to iterate over all organizations.
     */
    @Override
    public List<UUID> getAllOrganizationIds() {
        return organizationRepository.findAll().stream()
                .map(Organization::getId)
                .collect(Collectors.toList());
    }
}
