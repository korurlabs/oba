package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.mapper.OrganizationMemberMapper;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberQueryServiceImplUnitTest {

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private OrganizationMemberMapper organizationMemberMapper;


    @InjectMocks
    private OrganizationMemberQueryServiceImpl organizationMemberQueryServiceImpl;

    @Nested
    class ListOrganizationMembers {

        private User caller(UUID callerId) {
            User caller = User.builder().build();
            caller.setId(callerId);
            return caller;
        }

        @Test
        void activeMember_returnsMemberResponseList() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            User caller = caller(userId);

            Organization organization = Organization.builder().name("Test Org").build();
            organization.setId(organizationId);

            OrganizationMember member = OrganizationMember.builder()
                    .organization(organization)
                    .user(caller)
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            member.setId(memberId);

            OrganizationMemberResponse expectedResponse = new OrganizationMemberResponse(
                    memberId, userId, "user@example.com", null, null, OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE, UserKind.HUMAN, null, null, null);

            when(organizationMemberRepository.findByOrganization_Id(organizationId)).thenReturn(List.of(member));
            when(organizationMemberMapper.toResponse(member)).thenReturn(expectedResponse);

            // Act
            List<OrganizationMemberResponse> result =
                    organizationMemberQueryServiceImpl.listOrganizationMembers(caller, organizationId);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(memberId, result.getFirst().id());
        }

        @Test
        void agentAmongMembers_isLeftOutAndPeopleKeepMembershipIdOrder() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            User person = caller(UUID.randomUUID());
            User agent = caller(UUID.randomUUID());
            agent.setKind(UserKind.AGENT);
            OrganizationMember later = OrganizationMember.builder().user(person).build();
            later.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
            OrganizationMember earlier = OrganizationMember.builder().user(person).build();
            earlier.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            OrganizationMember agentMember = OrganizationMember.builder().user(agent).build();
            agentMember.setId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
            OrganizationMemberResponse earlierResponse = new OrganizationMemberResponse(earlier.getId(),
                    person.getId(), "a@example.com", null, null, OrganizationMemberRole.MEMBER,
                    OrganizationMemberStatus.ACTIVE, UserKind.HUMAN, null, null, null);
            OrganizationMemberResponse laterResponse = new OrganizationMemberResponse(later.getId(),
                    person.getId(), "b@example.com", null, null, OrganizationMemberRole.MEMBER,
                    OrganizationMemberStatus.ACTIVE, UserKind.HUMAN, null, null, null);
            when(organizationMemberRepository.findByOrganization_Id(organizationId))
                    .thenReturn(List.of(later, agentMember, earlier));
            when(organizationMemberMapper.toResponse(earlier)).thenReturn(earlierResponse);
            when(organizationMemberMapper.toResponse(later)).thenReturn(laterResponse);

            // Act
            List<OrganizationMemberResponse> result =
                    organizationMemberQueryServiceImpl.listOrganizationMembers(person, organizationId);

            // Assert
            assertEquals(List.of(earlierResponse, laterResponse), result);
            verify(organizationMemberMapper, never()).toResponse(agentMember);
        }

        @Test
        void organizationHasNoMembers_returnsEmptyList() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            User caller = caller(UUID.randomUUID());

            when(organizationMemberRepository.findByOrganization_Id(organizationId))
                    .thenReturn(Collections.emptyList());

            // Act
            List<OrganizationMemberResponse> result =
                    organizationMemberQueryServiceImpl.listOrganizationMembers(caller, organizationId);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class FindMember {

        @Test
        void memberExists_returnsOptionalWithMember() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            OrganizationMember member = OrganizationMember.builder()
                    .role(OrganizationMemberRole.MEMBER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .build();
            member.setId(UUID.randomUUID());

            when(organizationMemberRepository.findByOrganization_IdAndUser_Id(organizationId, userId))
                    .thenReturn(Optional.of(member));

            // Act
            Optional<OrganizationMember> result =
                    organizationMemberQueryServiceImpl.findMember(organizationId, userId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(member.getId(), result.get().getId());
        }

        @Test
        void memberDoesNotExist_returnsEmptyOptional() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(organizationMemberRepository.findByOrganization_IdAndUser_Id(organizationId, userId))
                    .thenReturn(Optional.empty());

            // Act
            Optional<OrganizationMember> result =
                    organizationMemberQueryServiceImpl.findMember(organizationId, userId);

            // Assert
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class CountActiveMembers {

        @Test
        void organization_returnsTheRepositoryCount() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            when(organizationMemberRepository.countActiveMembers(organizationId)).thenReturn(7L);

            // Act
            long count = organizationMemberQueryServiceImpl.countActiveMembers(organizationId);

            // Assert
            assertEquals(7L, count);
        }
    }

    @Nested
    class FindActiveUserIdsByRoles {

        @Test
        void ownerAndAdmin_queriesByRoleNamesAndReturnsTheUsers() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            when(organizationMemberRepository.findActiveUserIdsByRoles(eq(organizationId), anyCollection()))
                    .thenReturn(List.of(ownerId, adminId));

            // Act
            Set<UUID> userIds = organizationMemberQueryServiceImpl.findActiveUserIdsByRoles(organizationId,
                    EnumSet.of(OrganizationMemberRole.OWNER, OrganizationMemberRole.ADMIN));

            // Assert
            assertEquals(Set.of(ownerId, adminId), userIds);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<String>> roles = ArgumentCaptor.forClass(Collection.class);
            verify(organizationMemberRepository).findActiveUserIdsByRoles(eq(organizationId), roles.capture());
            assertEquals(Set.of("OWNER", "ADMIN"), new HashSet<>(roles.getValue()));
        }

        @Test
        void noRoles_findsNobodyWithoutAQuery() {
            // Act
            Set<UUID> userIds = organizationMemberQueryServiceImpl.findActiveUserIdsByRoles(UUID.randomUUID(),
                    Set.of());

            // Assert
            assertTrue(userIds.isEmpty());
            verifyNoInteractions(organizationMemberRepository);
        }
    }

    @Nested
    class FindActiveHumanUserIdsByRoles {

        @Test
        void roles_queriesThePeopleOnlyByRoleNames() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID personId = UUID.randomUUID();
            when(organizationMemberRepository.findActiveHumanUserIdsByRoles(eq(organizationId), anyCollection()))
                    .thenReturn(List.of(personId));

            // Act
            Set<UUID> userIds = organizationMemberQueryServiceImpl.findActiveHumanUserIdsByRoles(organizationId,
                    EnumSet.of(OrganizationMemberRole.MEMBER, OrganizationMemberRole.GUEST));

            // Assert
            assertEquals(Set.of(personId), userIds);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<String>> roles = ArgumentCaptor.forClass(Collection.class);
            verify(organizationMemberRepository).findActiveHumanUserIdsByRoles(eq(organizationId), roles.capture());
            assertEquals(Set.of("MEMBER", "GUEST"), new HashSet<>(roles.getValue()));
            verify(organizationMemberRepository, never()).findActiveUserIdsByRoles(any(), anyCollection());
        }

        @Test
        void noRoles_findsNobodyWithoutAQuery() {
            // Act
            Set<UUID> userIds = organizationMemberQueryServiceImpl.findActiveHumanUserIdsByRoles(UUID.randomUUID(),
                    Set.of());

            // Assert
            assertTrue(userIds.isEmpty());
            verifyNoInteractions(organizationMemberRepository);
        }
    }
}
