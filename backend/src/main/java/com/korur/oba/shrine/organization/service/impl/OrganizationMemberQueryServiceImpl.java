package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.mapper.OrganizationMemberMapper;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

/**
 * Implements read operations for organization membership.
 *
 * All methods run within a read-only transaction for optimal database
 * performance.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationMemberQueryServiceImpl implements OrganizationMemberQueryService {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationMemberMapper organizationMemberMapper;
    private final OrganizationValidator organizationValidator;

    @Override
    public List<OrganizationMemberResponse> listOrganizationMembers(User user, UUID organizationId) {
        // Map each membership to a response DTO.
        return organizationMemberRepository.findByOrganization_Id(organizationId)
                .stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(organizationMemberMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<OrganizationMember> findMember(UUID organizationId, UUID userId) {
        return organizationMemberRepository.findByOrganization_IdAndUser_Id(organizationId, userId);
    }

    @Override
    public Set<UUID> getColleagueUserIds(UUID userId) {
        Set<UUID> colleagueIds = new HashSet<>();
        colleagueIds.add(userId);

        // A disabled membership or a disabled organization grants no access, so neither
        // makes anyone a colleague.
        List<OrganizationMember> memberships = organizationMemberRepository
                .findByUser_IdAndStatus(userId, OrganizationMemberStatus.ACTIVE);
        for (OrganizationMember membership : memberships) {
            if (membership.getOrganization().getStatus() != OrganizationStatus.ACTIVE) {
                continue;
            }
            List<OrganizationMember> organizationMembers = organizationMemberRepository
                    .findByOrganization_IdAndStatus(membership.getOrganization().getId(),
                            OrganizationMemberStatus.ACTIVE);
            for (OrganizationMember organizationMember : organizationMembers) {
                colleagueIds.add(organizationMember.getUser().getId());
            }
        }
        return colleagueIds;
    }

    @Override
    public List<OrganizationMemberSummaryProjection> listActiveMemberSummaries(UUID organizationId,
                                                                               Instant joinedBefore) {
        return organizationMemberRepository.findActiveMemberSummaries(organizationId, joinedBefore);
    }

    @Override
    public Optional<UUID> findPrimaryActiveOrganizationId(UUID userId) {
        return organizationMemberRepository.findPrimaryActiveOrganizationId(userId);
    }

    @Override
    public long countActiveMembers(UUID organizationId) {
        return organizationMemberRepository.countActiveMembers(organizationId);
    }

    @Override
    public Set<UUID> findActiveUserIdsByRoles(UUID organizationId, Set<OrganizationMemberRole> roles) {
        if (roles.isEmpty()) {
            return Set.of();
        }
        List<String> roleNames = roles.stream()
                .map(OrganizationMemberRole::name)
                .toList();
        return new HashSet<>(organizationMemberRepository.findActiveUserIdsByRoles(organizationId, roleNames));
    }

    @Override
    public void ensureNotSoleOwnerOfAnyOrganization(UUID userId) {
        // Only an ACTIVE membership counts towards the active owners of an organization,
        // so a disabled membership cannot make its holder the sole owner.
        List<OrganizationMember> memberships = organizationMemberRepository
                .findByUser_IdAndStatus(userId, OrganizationMemberStatus.ACTIVE);
        for (OrganizationMember membership : memberships) {
            organizationValidator.ensureNotSoleOwner(membership.getOrganization().getId(), membership);
        }
    }
}
