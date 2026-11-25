package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupCreateRequest;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupMemberAddRequest;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupUpdateRequest;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupResponse;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.helper.AccessHelper;
import com.korur.oba.shrine.access.mapper.OrganizationGroupMapper;
import com.korur.oba.shrine.access.mapper.OrganizationGroupMapperImpl;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.repository.OrganizationGroupMemberRepository;
import com.korur.oba.shrine.access.repository.OrganizationGroupRepository;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.service.AppAccessRevocationService;
import com.korur.oba.shrine.access.service.OrganizationGroupQueryService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.validator.OrganizationGroupValidator;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

@ExtendWith(MockitoExtension.class)
class OrganizationGroupServiceImplUnitTest {

    @Mock
    private OrganizationGroupRepository organizationGroupRepository;

    @Mock
    private OrganizationGroupMemberRepository organizationGroupMemberRepository;

    @Mock
    private AppGrantRepository appGrantRepository;

    @Spy
    private OrganizationGroupMapper organizationGroupMapper = new OrganizationGroupMapperImpl();

    @Mock
    private OrganizationGroupQueryService organizationGroupQueryService;

    @Mock
    private OrganizationQueryService organizationQueryService;

    @Mock
    private OrganizationValidator organizationValidator;

    @Mock
    private OrganizationGroupValidator organizationGroupValidator;

    // The real validator, so the refusal code and payload are the production ones.
    @Spy
    private AgentValidator agentValidator = new AgentValidator(mock(UserQueryService.class), new LoggingService());

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @Mock
    private AppAccessRevocationService appAccessRevocationService;

    @Mock
    private AccessHelper accessHelper;

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private OrganizationGroupServiceImpl organizationGroupServiceImpl;

    private static User actor() {
        User actor = User.builder().build();
        actor.setId(UUID.randomUUID());
        return actor;
    }

    private static Organization organization(UUID organizationId) {
        Organization organization = Organization.builder().name("Acme").build();
        organization.setId(organizationId);
        return organization;
    }

    private static OrganizationGroup customGroup(Organization organization, UUID groupId) {
        OrganizationGroup group = OrganizationGroup.builder()
                .organization(organization)
                .name("Engineering")
                .description("Developers")
                .build();
        group.setId(groupId);
        return group;
    }

    @Nested
    class CreateGroup {

        @Test
        void nameWithSurroundingWhitespaceTaken_checksTrimmedNameAndThrowsConflictException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            when(organizationQueryService.getOrganizationEntityById(organizationId))
                    .thenReturn(organization(organizationId));
            when(organizationGroupRepository.existsByOrganization_IdAndNameIgnoreCase(organizationId, "Everyone"))
                    .thenReturn(true);
            OrganizationGroupCreateRequest request = new OrganizationGroupCreateRequest("Everyone ", null);

            // Act
            assertThrows(ConflictException.class,
                    () -> organizationGroupServiceImpl.createGroup(actor(), organizationId, request));

            // Assert
            verify(organizationGroupRepository, never()).save(any(OrganizationGroup.class));
        }

        @Test
        void nameWithSurroundingWhitespaceFree_savesTrimmedNameAndDescription() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            when(organizationQueryService.getOrganizationEntityById(organizationId))
                    .thenReturn(organization(organizationId));
            when(organizationGroupRepository.save(any(OrganizationGroup.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            OrganizationGroupCreateRequest request =
                    new OrganizationGroupCreateRequest(" Engineering  ", " Developers ");

            // Act
            OrganizationGroupResponse response =
                    organizationGroupServiceImpl.createGroup(actor(), organizationId, request);

            // Assert
            ArgumentCaptor<OrganizationGroup> saved = ArgumentCaptor.forClass(OrganizationGroup.class);
            verify(organizationGroupRepository).save(saved.capture());
            verify(organizationGroupRepository).existsByOrganization_IdAndNameIgnoreCase(organizationId, "Engineering");
            assertEquals("Engineering", saved.getValue().getName());
            assertEquals("Developers", saved.getValue().getDescription());
            assertEquals("Engineering", response.name());
        }
    }

    @Nested
    class UpdateGroup {

        @Test
        void nameWithSurroundingWhitespaceTaken_checksTrimmedNameAndThrowsConflictException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            OrganizationGroup group = customGroup(organization(organizationId), groupId);
            when(organizationGroupQueryService.getGroupEntity(organizationId, groupId)).thenReturn(group);
            when(organizationGroupRepository.existsByOrganization_IdAndNameIgnoreCaseAndIdNot(
                    organizationId, "Platform", groupId)).thenReturn(true);
            OrganizationGroupUpdateRequest request = new OrganizationGroupUpdateRequest("Platform ", null);

            // Act
            assertThrows(ConflictException.class,
                    () -> organizationGroupServiceImpl.updateGroup(actor(), organizationId, groupId, request));

            // Assert
            assertEquals("Engineering", group.getName());
            verify(organizationGroupRepository, never()).save(any(OrganizationGroup.class));
        }

        @Test
        void nameWithSurroundingWhitespaceFree_storesTrimmedNameAndDescription() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            OrganizationGroup group = customGroup(organization(organizationId), groupId);
            when(organizationGroupQueryService.getGroupEntity(organizationId, groupId)).thenReturn(group);
            when(organizationGroupRepository.save(group)).thenReturn(group);
            OrganizationGroupUpdateRequest request =
                    new OrganizationGroupUpdateRequest("  Platform ", " Platform team ");

            // Act
            OrganizationGroupResponse response =
                    organizationGroupServiceImpl.updateGroup(actor(), organizationId, groupId, request);

            // Assert
            verify(organizationGroupRepository).existsByOrganization_IdAndNameIgnoreCaseAndIdNot(
                    organizationId, "Platform", groupId);
            assertEquals("Platform", group.getName());
            assertEquals("Platform team", group.getDescription());
            assertEquals("Platform", response.name());
        }
    }

    private static OrganizationGroup everyoneGroup(Organization organization, UUID groupId) {
        OrganizationGroup group = OrganizationGroup.builder()
                .organization(organization)
                .name("Everyone")
                .type(OrganizationGroupType.EVERYONE)
                .build();
        group.setId(groupId);
        return group;
    }

    private static User userWithId() {
        User user = User.builder().email("ada@example.com").build();
        user.setId(UUID.randomUUID());
        return user;
    }

    @Nested
    class EveryoneGroupIsImmutable {

        private UUID organizationId;
        private UUID groupId;

        private void stubEveryone() {
            organizationId = UUID.randomUUID();
            groupId = UUID.randomUUID();
            OrganizationGroup everyone = everyoneGroup(organization(organizationId), groupId);
            when(organizationGroupQueryService.getGroupEntity(organizationId, groupId)).thenReturn(everyone);
            doThrow(new ConflictException(ExceptionDomainAccessError.STA_SYSTEM_GROUP_IMMUTABLE, Map.of()))
                    .when(organizationGroupValidator).ensureCustomGroup(organizationId, everyone);
        }

        @Test
        void updateGroup_everyone_throwsSystemGroupImmutable() {
            stubEveryone();

            ConflictException exception = assertThrows(ConflictException.class, () ->
                    organizationGroupServiceImpl.updateGroup(actor(), organizationId, groupId,
                            new OrganizationGroupUpdateRequest("Renamed", null)));

            assertEquals("APP_STA_001", exception.getErrorCode());
            verify(organizationGroupRepository, never()).save(any(OrganizationGroup.class));
        }

        @Test
        void deleteGroup_everyone_throwsSystemGroupImmutable() {
            stubEveryone();

            ConflictException exception = assertThrows(ConflictException.class,
                    () -> organizationGroupServiceImpl.deleteGroup(actor(), organizationId, groupId));

            assertEquals("APP_STA_001", exception.getErrorCode());
            verify(organizationGroupRepository, never()).delete(any(OrganizationGroup.class));
            verifyNoInteractions(appGrantRepository);
        }

        @Test
        void addMembers_everyone_throwsSystemGroupImmutable() {
            stubEveryone();

            ConflictException exception = assertThrows(ConflictException.class, () ->
                    organizationGroupServiceImpl.addMembers(actor(), organizationId, groupId,
                            new OrganizationGroupMemberAddRequest(List.of(UUID.randomUUID()))));

            assertEquals("APP_STA_001", exception.getErrorCode());
            verifyNoInteractions(organizationValidator);
            verify(organizationGroupMemberRepository, never()).saveAll(any());
        }

        @Test
        void removeMember_everyone_throwsSystemGroupImmutable() {
            stubEveryone();

            ConflictException exception = assertThrows(ConflictException.class, () ->
                    organizationGroupServiceImpl.removeMember(actor(), organizationId, groupId, UUID.randomUUID()));

            assertEquals("APP_STA_001", exception.getErrorCode());
            verify(organizationGroupMemberRepository, never()).delete(any(OrganizationGroupMember.class));
        }
    }

    @Nested
    class DeleteGroup {

        @Test
        void customGroup_softDeletesMemberRowsGrantsAndGroupThenPublishes() {
            UUID organizationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            OrganizationGroup group = customGroup(organization(organizationId), groupId);
            User member = userWithId();
            OrganizationGroupMember row = OrganizationGroupMember.builder().group(group).user(member).build();
            AppGrant kazan = AppGrant.builder().group(group).app(App.KAZAN).build();
            AppGrant kaya = AppGrant.builder().group(group).app(App.KAYA).build();
            Map<App, Set<UUID>> holders = Map.of(
                    App.KAZAN, Set.of(member.getId()), App.KAYA, Set.of(member.getId()));
            when(organizationGroupQueryService.getGroupEntity(organizationId, groupId)).thenReturn(group);
            when(organizationGroupMemberRepository.findByGroup_Id(groupId)).thenReturn(List.of(row));
            when(appGrantRepository.findByGroup_Id(groupId)).thenReturn(List.of(kazan, kaya));
            when(accessHelper.appsOf(List.of(kazan, kaya))).thenReturn(List.of(App.KAZAN, App.KAYA));
            when(accessHelper.userIdsOf(List.of(row))).thenReturn(Set.of(member.getId()));
            when(appAccessQueryService.findAppHolders(organizationId, List.of(App.KAZAN, App.KAYA),
                    Set.of(member.getId()))).thenReturn(holders);

            organizationGroupServiceImpl.deleteGroup(actor(), organizationId, groupId);

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(appAccessQueryService, appAccessRevocationService,
                    organizationGroupRepository);
            order.verify(appAccessQueryService).findAppHolders(organizationId,
                    List.of(App.KAZAN, App.KAYA), Set.of(member.getId()));
            order.verify(organizationGroupRepository).delete(group);
            order.verify(appAccessRevocationService).publishLostAppAccess(organizationId, holders);
            verify(organizationGroupMemberRepository).deleteAll(List.of(row));
            verify(appGrantRepository).deleteAll(List.of(kazan, kaya));
        }
    }

    @Nested
    class AddMembers {

        @Test
        void mixOfNewAndExistingUsers_savesRowsOnlyForNewUsers() {
            UUID organizationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            OrganizationGroup group = customGroup(organization(organizationId), groupId);
            User fresh = userWithId();
            User already = userWithId();
            when(organizationGroupQueryService.getGroupEntity(organizationId, groupId)).thenReturn(group);
            when(organizationValidator.ensureMemberExists(organizationId, fresh.getId()))
                    .thenReturn(OrganizationMember.builder().user(fresh).build());
            when(organizationValidator.ensureMemberExists(organizationId, already.getId()))
                    .thenReturn(OrganizationMember.builder().user(already).build());
            when(organizationGroupMemberRepository.existsByGroup_IdAndUser_Id(groupId, fresh.getId())).thenReturn(false);
            when(organizationGroupMemberRepository.existsByGroup_IdAndUser_Id(groupId, already.getId())).thenReturn(true);

            organizationGroupServiceImpl.addMembers(actor(), organizationId, groupId,
                    new OrganizationGroupMemberAddRequest(List.of(fresh.getId(), already.getId(), fresh.getId())));

            ArgumentCaptor<List<OrganizationGroupMember>> saved = ArgumentCaptor.forClass(List.class);
            verify(organizationGroupMemberRepository).saveAll(saved.capture());
            assertEquals(1, saved.getValue().size());
            assertSame(fresh, saved.getValue().get(0).getUser());
        }

        @Test
        void userNotInOrganization_refusesTheWholeRequestBeforeAnyWrite() {
            UUID organizationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            UUID stranger = UUID.randomUUID();
            when(organizationGroupQueryService.getGroupEntity(organizationId, groupId))
                    .thenReturn(customGroup(organization(organizationId), groupId));
            when(organizationValidator.ensureMemberExists(organizationId, stranger))
                    .thenThrow(new NotFoundException());

            assertThrows(NotFoundException.class, () -> organizationGroupServiceImpl.addMembers(actor(),
                    organizationId, groupId, new OrganizationGroupMemberAddRequest(List.of(stranger))));

            verify(organizationGroupMemberRepository, never()).saveAll(any());
        }

        @Test
        void agentAmongUsers_refusesTheWholeRequestWithAgentTargetRefused() {
            UUID organizationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            User person = userWithId();
            User agent = User.builder().email("agent@agents.oba.invalid").kind(UserKind.AGENT).build();
            agent.setId(UUID.randomUUID());
            when(organizationGroupQueryService.getGroupEntity(organizationId, groupId))
                    .thenReturn(customGroup(organization(organizationId), groupId));
            when(organizationValidator.ensureMemberExists(organizationId, person.getId()))
                    .thenReturn(OrganizationMember.builder().user(person).build());
            when(organizationValidator.ensureMemberExists(organizationId, agent.getId()))
                    .thenReturn(OrganizationMember.builder().user(agent).build());

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> organizationGroupServiceImpl.addMembers(actor(), organizationId, groupId,
                            new OrganizationGroupMemberAddRequest(List.of(person.getId(), agent.getId()))));

            assertEquals("AGT_VAL_001", exception.getErrorCode());
            assertEquals(AccessConstant.AGENT_ACTION_GROUP_MEMBER_ADD,
                    exception.getErrorValues().get(AgentConstant.FIELD_ACTION));
            assertEquals(agent.getId().toString(), exception.getErrorValues().get(AgentConstant.FIELD_USER_ID));
            verify(organizationGroupMemberRepository, never()).existsByGroup_IdAndUser_Id(any(), any());
            verify(organizationGroupMemberRepository, never()).saveAll(any());
            verifyNoInteractions(auditService);
        }
    }

    @Nested
    class RemoveMember {

        @Test
        void rowMissing_propagatesGroupMemberNotFoundAndWritesNothing() {
            UUID organizationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(organizationGroupQueryService.getGroupEntity(organizationId, groupId))
                    .thenReturn(customGroup(organization(organizationId), groupId));
            when(organizationGroupValidator.ensureGroupMemberExists(organizationId, groupId, userId))
                    .thenThrow(new NotFoundException(ExceptionDomainAccessError.NOT_GROUP_MEMBER_FOUND, Map.of()));

            NotFoundException exception = assertThrows(NotFoundException.class, () ->
                    organizationGroupServiceImpl.removeMember(actor(), organizationId, groupId, userId));

            assertEquals("APP_NOT_003", exception.getErrorCode());
            verify(organizationGroupMemberRepository, never()).delete(any(OrganizationGroupMember.class));
            verifyNoInteractions(auditService);
        }

        @Test
        void groupWithApps_publishesForTheAppsTheRemovedUserLost() {
            UUID organizationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            OrganizationGroup group = customGroup(organization(organizationId), groupId);
            OrganizationGroupMember row = OrganizationGroupMember.builder().group(group).user(userWithId()).build();
            List<AppGrant> grants = List.of(AppGrant.builder().group(group).app(App.KAYA).build());
            Map<App, Set<UUID>> holders = Map.of(App.KAYA, Set.of(userId));
            when(organizationGroupQueryService.getGroupEntity(organizationId, groupId)).thenReturn(group);
            when(organizationGroupValidator.ensureGroupMemberExists(organizationId, groupId, userId))
                    .thenReturn(row);
            when(appGrantRepository.findByGroup_Id(groupId)).thenReturn(grants);
            when(accessHelper.appsOf(grants)).thenReturn(List.of(App.KAYA));
            when(appAccessQueryService.findAppHolders(organizationId, List.of(App.KAYA), Set.of(userId)))
                    .thenReturn(holders);

            organizationGroupServiceImpl.removeMember(actor(), organizationId, groupId, userId);

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(appAccessQueryService, appAccessRevocationService,
                    organizationGroupMemberRepository);
            order.verify(appAccessQueryService).findAppHolders(organizationId, List.of(App.KAYA), Set.of(userId));
            order.verify(organizationGroupMemberRepository).delete(row);
            order.verify(appAccessRevocationService).publishLostAppAccess(organizationId, holders);
        }
    }

    @Nested
    class CreateEveryoneGroup {

        @Test
        void noGroupYet_savesAnEveryoneGroupWithTheFixedName() {
            Organization organization = organization(UUID.randomUUID());
            when(organizationGroupRepository.findFirstByOrganization_IdAndType(
                    organization.getId(), OrganizationGroupType.EVERYONE)).thenReturn(Optional.empty());
            when(organizationGroupRepository.save(any(OrganizationGroup.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            organizationGroupServiceImpl.createEveryoneGroup(organization);

            ArgumentCaptor<OrganizationGroup> saved = ArgumentCaptor.forClass(OrganizationGroup.class);
            verify(organizationGroupRepository).save(saved.capture());
            assertEquals("Everyone", saved.getValue().getName());
            assertEquals(OrganizationGroupType.EVERYONE, saved.getValue().getType());
            assertSame(organization, saved.getValue().getOrganization());
        }

        @Test
        @SuppressWarnings("unchecked")
        void noGroupYet_grantsEveryAppToTheNewGroupWithNoGrantingUserAndNoAudit() {
            Organization organization = organization(UUID.randomUUID());
            when(organizationGroupRepository.findFirstByOrganization_IdAndType(
                    organization.getId(), OrganizationGroupType.EVERYONE)).thenReturn(Optional.empty());
            when(organizationGroupRepository.save(any(OrganizationGroup.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            organizationGroupServiceImpl.createEveryoneGroup(organization);

            ArgumentCaptor<OrganizationGroup> savedGroup = ArgumentCaptor.forClass(OrganizationGroup.class);
            verify(organizationGroupRepository).save(savedGroup.capture());
            ArgumentCaptor<List<AppGrant>> savedGrants = ArgumentCaptor.forClass(List.class);
            verify(appGrantRepository).saveAll(savedGrants.capture());
            List<AppGrant> grants = savedGrants.getValue();
            assertEquals(List.of(App.values()), grants.stream().map(AppGrant::getApp).toList());
            for (AppGrant grant : grants) {
                assertSame(organization, grant.getOrganization());
                assertSame(savedGroup.getValue(), grant.getGroup());
                assertNull(grant.getUser());
                assertNull(grant.getGrantedBy());
            }
            verifyNoInteractions(auditService);
        }

        @Test
        void groupExists_savesNothing() {
            Organization organization = organization(UUID.randomUUID());
            when(organizationGroupRepository.findFirstByOrganization_IdAndType(
                    organization.getId(), OrganizationGroupType.EVERYONE))
                    .thenReturn(Optional.of(everyoneGroup(organization, UUID.randomUUID())));

            organizationGroupServiceImpl.createEveryoneGroup(organization);

            verify(organizationGroupRepository, never()).save(any(OrganizationGroup.class));
            verifyNoInteractions(appGrantRepository);
        }
    }
}
