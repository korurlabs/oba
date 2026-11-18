package com.korur.oba.shrine.access.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.dto.request.AppGrantCreateRequest;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.helper.AccessHelper;
import com.korur.oba.shrine.access.mapper.AppAccessMapper;
import com.korur.oba.shrine.access.mapper.AppAccessMapperImpl;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.repository.OrganizationGroupMemberRepository;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.service.AppAccessRevocationService;
import com.korur.oba.shrine.access.service.OrganizationGroupQueryService;
import com.korur.oba.shrine.access.validator.AppGrantValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.validator.OrganizationValidator;

@ExtendWith(MockitoExtension.class)
class AppAccessServiceImplUnitTest {

    @Mock
    private AppGrantRepository appGrantRepository;

    @Mock
    private OrganizationGroupMemberRepository organizationGroupMemberRepository;

    @Spy
    private AppAccessMapper appAccessMapper = new AppAccessMapperImpl();

    @Mock
    private OrganizationGroupQueryService organizationGroupQueryService;

    @Mock
    private OrganizationValidator organizationValidator;

    @Mock
    private AppGrantValidator appGrantValidator;

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
    private AppAccessServiceImpl appAccessServiceImpl;

    private static User user() {
        User user = User.builder().email("ada@example.com").build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static Organization organization(UUID organizationId) {
        Organization organization = Organization.builder().name("Acme").build();
        organization.setId(organizationId);
        return organization;
    }

    private static OrganizationGroup group(Organization organization) {
        OrganizationGroup group = OrganizationGroup.builder().organization(organization).name("Dev").build();
        group.setId(UUID.randomUUID());
        return group;
    }

    @Nested
    class Grant {

        @Test
        void targetRefused_propagatesValidationExceptionAndWritesNothing() {
            UUID organizationId = UUID.randomUUID();
            AppGrantCreateRequest request = new AppGrantCreateRequest(App.KAIZEN, null, null);
            doThrow(new ValidationException(ExceptionDomainAccessError.VAL_GRANT_TARGET_INVALID, Map.of()))
                    .when(appGrantValidator).ensureOneTarget(organizationId, request);

            ValidationException exception = assertThrows(ValidationException.class,
                    () -> appAccessServiceImpl.grant(user(), organizationId, request));

            assertEquals("APP_VAL_001", exception.getErrorCode());
            verify(appGrantRepository, never()).save(any(AppGrant.class));
            verify(organizationValidator, never()).ensureMemberExists(any(), any());
        }

        @Test
        void userNotMemberOfOrganization_propagatesAndWritesNothing() {
            UUID organizationId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            when(organizationValidator.ensureMemberExists(organizationId, targetId))
                    .thenThrow(new NotFoundException());

            assertThrows(NotFoundException.class, () -> appAccessServiceImpl.grant(user(), organizationId,
                    new AppGrantCreateRequest(App.KAIZEN, targetId, null)));

            verify(appGrantRepository, never()).save(any(AppGrant.class));
        }

        @Test
        void groupOfAnotherOrganization_propagatesNotFoundAndWritesNothing() {
            UUID organizationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            when(organizationGroupQueryService.getGroupEntity(organizationId, groupId))
                    .thenThrow(new NotFoundException());

            assertThrows(NotFoundException.class, () -> appAccessServiceImpl.grant(user(), organizationId,
                    new AppGrantCreateRequest(App.KAYA, null, groupId)));

            verify(appGrantRepository, never()).save(any(AppGrant.class));
        }

        @Test
        void existingUserGrant_isReturnedWithoutSave() {
            UUID organizationId = UUID.randomUUID();
            Organization organization = organization(organizationId);
            User target = user();
            OrganizationMember member = OrganizationMember.builder().organization(organization).user(target).build();
            AppGrant existing = AppGrant.builder().organization(organization).app(App.KAIZEN).user(target).build();
            existing.setId(UUID.randomUUID());
            when(organizationValidator.ensureMemberExists(organizationId, target.getId())).thenReturn(member);
            when(appGrantRepository.findByOrganization_IdAndAppAndUser_Id(organizationId, App.KAIZEN, target.getId()))
                    .thenReturn(Optional.of(existing));

            AppGrantResponse response = appAccessServiceImpl.grant(user(), organizationId,
                    new AppGrantCreateRequest(App.KAIZEN, target.getId(), null));

            assertEquals(existing.getId(), response.id());
            verify(appGrantRepository, never()).save(any(AppGrant.class));
            verify(auditService, never()).log(any());
        }

        @Test
        void existingGroupGrant_isReturnedWithoutSave() {
            UUID organizationId = UUID.randomUUID();
            OrganizationGroup group = group(organization(organizationId));
            AppGrant existing = AppGrant.builder().organization(group.getOrganization()).app(App.KAYA)
                    .group(group).build();
            existing.setId(UUID.randomUUID());
            when(organizationGroupQueryService.getGroupEntity(organizationId, group.getId())).thenReturn(group);
            when(appGrantRepository.findByGroup_IdAndApp(group.getId(), App.KAYA))
                    .thenReturn(Optional.of(existing));

            AppGrantResponse response = appAccessServiceImpl.grant(user(), organizationId,
                    new AppGrantCreateRequest(App.KAYA, null, group.getId()));

            assertEquals(existing.getId(), response.id());
            verify(appGrantRepository, never()).save(any(AppGrant.class));
        }

        @Test
        void newUserGrant_savesWithActorAndAudits() {
            UUID organizationId = UUID.randomUUID();
            Organization organization = organization(organizationId);
            User actor = user();
            User target = user();
            OrganizationMember member = OrganizationMember.builder().organization(organization).user(target).build();
            when(organizationValidator.ensureMemberExists(organizationId, target.getId())).thenReturn(member);
            when(appGrantRepository.findByOrganization_IdAndAppAndUser_Id(organizationId, App.KAYA, target.getId()))
                    .thenReturn(Optional.empty());
            when(appGrantRepository.save(any(AppGrant.class))).thenAnswer(invocation -> {
                AppGrant saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            AppGrantResponse response = appAccessServiceImpl.grant(actor, organizationId,
                    new AppGrantCreateRequest(App.KAYA, target.getId(), null));

            org.mockito.ArgumentCaptor<AppGrant> saved = org.mockito.ArgumentCaptor.forClass(AppGrant.class);
            verify(appGrantRepository).save(saved.capture());
            assertSame(actor, saved.getValue().getGrantedBy());
            assertSame(target, saved.getValue().getUser());
            assertSame(organization, saved.getValue().getOrganization());
            assertEquals(App.KAYA, response.app());
            verify(auditService).log(any());
        }
    }

    @Nested
    class Revoke {

        @Test
        void grantNotFound_propagatesNotFoundAndDeletesNothing() {
            UUID organizationId = UUID.randomUUID();
            UUID grantId = UUID.randomUUID();
            when(appGrantValidator.ensureGrantExists(organizationId, grantId))
                    .thenThrow(new NotFoundException(ExceptionDomainAccessError.NOT_GRANT_FOUND, Map.of()));

            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> appAccessServiceImpl.revoke(user(), organizationId, grantId));

            assertEquals("APP_NOT_002", exception.getErrorCode());
            verify(appGrantRepository, never()).delete(any(AppGrant.class));
        }

        @Test
        void userGrant_findsTheAppHoldersBeforeTheDeleteAndPublishesAfter() {
            UUID organizationId = UUID.randomUUID();
            User target = user();
            AppGrant grant = AppGrant.builder().organization(organization(organizationId)).app(App.KAIZEN)
                    .user(target).build();
            grant.setId(UUID.randomUUID());
            Map<App, Set<UUID>> holders = Map.of(App.KAIZEN, Set.of(target.getId()));
            when(appGrantValidator.ensureGrantExists(organizationId, grant.getId())).thenReturn(grant);
            when(appAccessQueryService.findTargetUserIds(grant)).thenReturn(Set.of(target.getId()));
            when(appAccessQueryService.findAppHolders(organizationId, Set.of(App.KAIZEN), Set.of(target.getId())))
                    .thenReturn(holders);

            appAccessServiceImpl.revoke(user(), organizationId, grant.getId());

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(appAccessQueryService, appGrantRepository,
                    appAccessRevocationService);
            order.verify(appAccessQueryService)
                    .findAppHolders(organizationId, Set.of(App.KAIZEN), Set.of(target.getId()));
            order.verify(appGrantRepository).delete(grant);
            order.verify(appAccessRevocationService).publishLostAppAccess(organizationId, holders);
        }

        @Test
        void kayaGroupGrant_looksForKayaHoldersAmongTheTargetUsers() {
            UUID organizationId = UUID.randomUUID();
            OrganizationGroup group = group(organization(organizationId));
            AppGrant grant = AppGrant.builder().organization(group.getOrganization()).app(App.KAYA)
                    .group(group).build();
            grant.setId(UUID.randomUUID());
            Set<UUID> groupUsers = Set.of(UUID.randomUUID(), UUID.randomUUID());
            Map<App, Set<UUID>> holders = Map.of(App.KAYA, groupUsers);
            when(appGrantValidator.ensureGrantExists(organizationId, grant.getId())).thenReturn(grant);
            when(appAccessQueryService.findTargetUserIds(grant)).thenReturn(groupUsers);
            when(appAccessQueryService.findAppHolders(organizationId, Set.of(App.KAYA), groupUsers))
                    .thenReturn(holders);

            appAccessServiceImpl.revoke(user(), organizationId, grant.getId());

            verify(appGrantRepository).delete(grant);
            verify(appAccessRevocationService).publishLostAppAccess(organizationId, holders);
        }
    }

    @Nested
    class RemoveMemberAccess {

        @Test
        void rowsAndGrants_areDeletedAndAudited() {
            UUID organizationId = UUID.randomUUID();
            User target = user();
            OrganizationGroupMember row = OrganizationGroupMember.builder().user(target).build();
            row.setId(UUID.randomUUID());
            AppGrant grant = AppGrant.builder().app(App.KAYA).user(target).build();
            grant.setId(UUID.randomUUID());
            when(organizationGroupMemberRepository.findByGroup_Organization_IdAndUser_Id(organizationId, target.getId()))
                    .thenReturn(List.of(row));
            when(appGrantRepository.findByOrganization_IdAndUser_Id(organizationId, target.getId()))
                    .thenReturn(List.of(grant));

            appAccessServiceImpl.removeMemberAccess(user(), organizationId, target.getId());

            verify(organizationGroupMemberRepository).deleteAll(List.of(row));
            verify(appGrantRepository).deleteAll(List.of(grant));
            verify(auditService).log(any());
        }

        @Test
        void nothingToRemove_writesNoAuditEntry() {
            UUID organizationId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(organizationGroupMemberRepository.findByGroup_Organization_IdAndUser_Id(organizationId, userId))
                    .thenReturn(List.of());
            when(appGrantRepository.findByOrganization_IdAndUser_Id(organizationId, userId)).thenReturn(List.of());

            appAccessServiceImpl.removeMemberAccess(user(), organizationId, userId);

            verify(appGrantRepository, never()).deleteAll(any());
            verify(auditService, never()).log(any());
        }
    }
}
