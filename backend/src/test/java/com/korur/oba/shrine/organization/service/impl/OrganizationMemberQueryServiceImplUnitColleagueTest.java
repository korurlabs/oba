package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberQueryServiceImplUnitColleagueTest {

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @InjectMocks
    private OrganizationMemberQueryServiceImpl organizationMemberQueryServiceImpl;

    private static User user() {
        User user = User.builder().build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static Organization organization(OrganizationStatus status) {
        Organization organization = Organization.builder().name("Org").status(status).build();
        organization.setId(UUID.randomUUID());
        return organization;
    }

    private static OrganizationMember membership(Organization organization, User user) {
        return OrganizationMember.builder().organization(organization).user(user).build();
    }

    @Nested
    class GetColleagueUserIds {

        @Test
        void sharedActiveOrganization_returnsCallerAndActiveMembers() {
            User caller = user();
            User colleague = user();
            Organization organization = organization(OrganizationStatus.ACTIVE);
            when(organizationMemberRepository.findByUser_IdAndStatus(caller.getId(), OrganizationMemberStatus.ACTIVE))
                    .thenReturn(List.of(membership(organization, caller)));
            when(organizationMemberRepository.findByOrganization_IdAndStatus(
                    organization.getId(), OrganizationMemberStatus.ACTIVE))
                    .thenReturn(List.of(membership(organization, caller), membership(organization, colleague)));

            Set<UUID> result = organizationMemberQueryServiceImpl.getColleagueUserIds(caller.getId());

            assertThat(result).containsExactlyInAnyOrder(caller.getId(), colleague.getId());
        }

        @Test
        void twoOrganizations_returnsMembersOfBothWithoutDuplicates() {
            User caller = user();
            User first = user();
            User second = user();
            Organization orgA = organization(OrganizationStatus.ACTIVE);
            Organization orgB = organization(OrganizationStatus.ACTIVE);
            when(organizationMemberRepository.findByUser_IdAndStatus(caller.getId(), OrganizationMemberStatus.ACTIVE))
                    .thenReturn(List.of(membership(orgA, caller), membership(orgB, caller)));
            when(organizationMemberRepository.findByOrganization_IdAndStatus(
                    orgA.getId(), OrganizationMemberStatus.ACTIVE))
                    .thenReturn(List.of(membership(orgA, caller), membership(orgA, first)));
            when(organizationMemberRepository.findByOrganization_IdAndStatus(
                    orgB.getId(), OrganizationMemberStatus.ACTIVE))
                    .thenReturn(List.of(membership(orgB, caller), membership(orgB, first), membership(orgB, second)));

            Set<UUID> result = organizationMemberQueryServiceImpl.getColleagueUserIds(caller.getId());

            assertThat(result).containsExactlyInAnyOrder(caller.getId(), first.getId(), second.getId());
        }

        @Test
        void disabledMembership_isExcludedBecauseOnlyActiveMembershipsAreRequested() {
            // The status filter lives in the derived repository queries, so the contract is the status asked for.
            User caller = user();
            when(organizationMemberRepository.findByUser_IdAndStatus(caller.getId(), OrganizationMemberStatus.ACTIVE))
                    .thenReturn(List.of());

            Set<UUID> result = organizationMemberQueryServiceImpl.getColleagueUserIds(caller.getId());

            assertThat(result).containsExactly(caller.getId());
            verify(organizationMemberRepository, never())
                    .findByUser_IdAndStatus(caller.getId(), OrganizationMemberStatus.DISABLED);
        }

        @Test
        void disabledOrganization_contributesNoColleagues() {
            User caller = user();
            Organization organization = organization(OrganizationStatus.DISABLED);
            when(organizationMemberRepository.findByUser_IdAndStatus(caller.getId(), OrganizationMemberStatus.ACTIVE))
                    .thenReturn(List.of(membership(organization, caller)));

            Set<UUID> result = organizationMemberQueryServiceImpl.getColleagueUserIds(caller.getId());

            assertThat(result).containsExactly(caller.getId());
            verify(organizationMemberRepository, never())
                    .findByOrganization_IdAndStatus(organization.getId(), OrganizationMemberStatus.ACTIVE);
        }

        @Test
        void noOrganization_returnsOnlyTheCaller() {
            User caller = user();
            when(organizationMemberRepository.findByUser_IdAndStatus(caller.getId(), OrganizationMemberStatus.ACTIVE))
                    .thenReturn(List.of());

            assertThat(organizationMemberQueryServiceImpl.getColleagueUserIds(caller.getId()))
                    .containsExactly(caller.getId());
        }
    }
}
