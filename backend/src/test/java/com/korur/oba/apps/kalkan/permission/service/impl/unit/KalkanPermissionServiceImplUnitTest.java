package com.korur.oba.apps.kalkan.permission.service.impl.unit;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.mock_data.KalkanMocks;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.permission.dto.request.KalkanPagePermissionRequest;
import com.korur.oba.apps.kalkan.permission.entity.KalkanPagePermission;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.apps.kalkan.permission.repository.KalkanPagePermissionRepository;
import com.korur.oba.apps.kalkan.permission.service.KalkanPermissionQueryService;
import com.korur.oba.apps.kalkan.permission.service.impl.KalkanPermissionServiceImpl;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KalkanPermissionServiceImplUnitTest {

    @Mock
    private KalkanPagePermissionRepository kalkanPagePermissionRepository;
    @Mock
    private KalkanPermissionQueryService kalkanPermissionQueryService;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private LoggingService loggingService;

    private static final Instant NOW = Instant.parse("2026-11-06T09:00:00Z");

    private KalkanPermissionServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final User caller = KalkanMocks.user();
    private final KalkanPage root = KalkanMocks.root(organizationId, "isms");

    @BeforeEach
    void setUp() {
        service = new KalkanPermissionServiceImpl(kalkanPagePermissionRepository, kalkanPermissionQueryService,
                organizationMemberQueryService, loggingService, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(kalkanPermissionQueryService.getManagedRoot(caller, organizationId, root.getId()))
                .thenReturn(root);
    }

    private User activeMember(OrganizationMemberRole role) {
        User user = KalkanMocks.user();
        when(organizationMemberQueryService.findMember(organizationId, user.getId()))
                .thenReturn(Optional.of(KalkanMocks.member(user, role)));
        return user;
    }

    private KalkanPagePermission live(User user, KalkanLevel level) {
        KalkanPagePermission permission = KalkanPagePermission.builder().page(root).userId(user.getId()).level(level)
                .createdById(caller.getId()).updatedById(caller.getId()).build();
        permission.setId(UUID.randomUUID());
        return permission;
    }

    private static KalkanPagePermissionRequest grant(User user, KalkanLevel level) {
        return new KalkanPagePermissionRequest(user.getId(), null, level);
    }

    private List<KalkanPagePermission> savedGrants() {
        ArgumentCaptor<List<KalkanPagePermission>> captor = ArgumentCaptor.forClass(List.class);
        verify(kalkanPagePermissionRepository).saveAllAndFlush(captor.capture());
        return captor.getValue();
    }

    @Nested
    class Diff {

        @Test
        void sameSubjectSameLevel_keepsTheRowAndWritesNothing() {
            User member = activeMember(OrganizationMemberRole.MEMBER);
            when(kalkanPagePermissionRepository.findByPage_IdOrderByCreatedAtAsc(root.getId()))
                    .thenReturn(List.of(live(member, KalkanLevel.EDIT)));

            service.replacePermissions(caller, organizationId, root.getId(), List.of(grant(member, KalkanLevel.EDIT)));

            verify(kalkanPagePermissionRepository, never()).softDeleteByIds(anyList(), any(), any());
            assertTrue(savedGrants().isEmpty());
        }

        @Test
        void subjectRemoved_softDeletesItsRowWithTheCaller() {
            User member = KalkanMocks.user();
            KalkanPagePermission row = live(member, KalkanLevel.EDIT);
            when(kalkanPagePermissionRepository.findByPage_IdOrderByCreatedAtAsc(root.getId())).thenReturn(List.of(row));

            service.replacePermissions(caller, organizationId, root.getId(), List.of());

            verify(kalkanPagePermissionRepository).softDeleteByIds(eq(List.of(row.getId())), eq(caller.getId()),
                    eq(NOW));
            assertTrue(savedGrants().isEmpty());
        }

        @Test
        void levelChanged_softDeletesOldRowAndInsertsNewOneByTheCaller() {
            User member = activeMember(OrganizationMemberRole.MEMBER);
            KalkanPagePermission row = live(member, KalkanLevel.VIEW);
            when(kalkanPagePermissionRepository.findByPage_IdOrderByCreatedAtAsc(root.getId())).thenReturn(List.of(row));

            service.replacePermissions(caller, organizationId, root.getId(), List.of(grant(member, KalkanLevel.EDIT)));

            verify(kalkanPagePermissionRepository).softDeleteByIds(eq(List.of(row.getId())), eq(caller.getId()),
                    eq(NOW));
            List<KalkanPagePermission> saved = savedGrants();
            assertEquals(1, saved.size());
            assertEquals(KalkanLevel.EDIT, saved.get(0).getLevel());
            assertEquals(member.getId(), saved.get(0).getUserId());
            assertEquals(caller.getId(), saved.get(0).getCreatedById());
            assertEquals(caller.getId(), saved.get(0).getUpdatedById());
        }

        @Test
        void subjectReGrantedAfterRevocation_getsANewRow() {
            User member = activeMember(OrganizationMemberRole.MEMBER);
            when(kalkanPagePermissionRepository.findByPage_IdOrderByCreatedAtAsc(root.getId())).thenReturn(List.of());

            service.replacePermissions(caller, organizationId, root.getId(), List.of(grant(member, KalkanLevel.VIEW)));

            verify(kalkanPagePermissionRepository, never()).softDeleteByIds(anyList(), any(), any());
            assertEquals(1, savedGrants().size());
        }

        @Test
        void mixedList_keepsOneRemovesOneAddsOne() {
            User kept = activeMember(OrganizationMemberRole.MEMBER);
            User added = activeMember(OrganizationMemberRole.ADMIN);
            User removed = KalkanMocks.user();
            KalkanPagePermission keptRow = live(kept, KalkanLevel.MANAGE);
            KalkanPagePermission removedRow = live(removed, KalkanLevel.VIEW);
            when(kalkanPagePermissionRepository.findByPage_IdOrderByCreatedAtAsc(root.getId()))
                    .thenReturn(List.of(keptRow, removedRow));

            service.replacePermissions(caller, organizationId, root.getId(),
                    List.of(grant(kept, KalkanLevel.MANAGE), grant(added, KalkanLevel.EDIT)));

            verify(kalkanPagePermissionRepository).softDeleteByIds(eq(List.of(removedRow.getId())),
                    eq(caller.getId()), eq(NOW));
            List<KalkanPagePermission> saved = savedGrants();
            assertEquals(1, saved.size());
            assertEquals(added.getId(), saved.get(0).getUserId());
        }
    }

    @Nested
    class Validation {

        private ValidationException refused(List<KalkanPagePermissionRequest> requests) {
            return assertThrows(ValidationException.class,
                    () -> service.replacePermissions(caller, organizationId, root.getId(), requests));
        }

        @Test
        void groupId_isRefusedAsGroupsNotSupported() {
            UUID groupId = UUID.randomUUID();

            ValidationException thrown = refused(List.of(new KalkanPagePermissionRequest(null, groupId, KalkanLevel.VIEW)));

            assertEquals("KLK_VAL_002", thrown.getErrorCode());
            assertEquals("GROUPS_NOT_SUPPORTED", thrown.getErrorValues().get("reason"));
            assertEquals(groupId.toString(), thrown.getErrorValues().get("groupId"));
        }

        @Test
        void missingUserId_isRefusedAsNoSubject() {
            ValidationException thrown = refused(List.of(new KalkanPagePermissionRequest(null, null, KalkanLevel.VIEW)));

            assertEquals("NO_SUBJECT", thrown.getErrorValues().get("reason"));
        }

        @Test
        void sameUserTwice_isRefusedAsDuplicateSubject() {
            User member = activeMember(OrganizationMemberRole.MEMBER);

            ValidationException thrown = refused(List.of(grant(member, KalkanLevel.VIEW), grant(member, KalkanLevel.EDIT)));

            assertEquals("DUPLICATE_SUBJECT", thrown.getErrorValues().get("reason"));
        }

        @Test
        void userWithoutMembership_isRefusedAsNotActiveMember() {
            User stranger = KalkanMocks.user();
            when(organizationMemberQueryService.findMember(organizationId, stranger.getId())).thenReturn(Optional.empty());

            ValidationException thrown = refused(List.of(grant(stranger, KalkanLevel.VIEW)));

            assertEquals("NOT_ACTIVE_MEMBER", thrown.getErrorValues().get("reason"));
        }

        @Test
        void disabledMembership_isRefusedAsNotActiveMember() {
            User user = KalkanMocks.user();
            when(organizationMemberQueryService.findMember(organizationId, user.getId())).thenReturn(Optional.of(
                    KalkanMocks.member(user, OrganizationMemberRole.MEMBER, OrganizationMemberStatus.DISABLED)));

            ValidationException thrown = refused(List.of(grant(user, KalkanLevel.VIEW)));

            assertEquals("NOT_ACTIVE_MEMBER", thrown.getErrorValues().get("reason"));
        }

        @Test
        void disabledUserAccount_isRefusedAsNotActiveMember() {
            User user = KalkanMocks.user();
            user.setStatus(UserStatus.DISABLED);
            when(organizationMemberQueryService.findMember(organizationId, user.getId()))
                    .thenReturn(Optional.of(KalkanMocks.member(user, OrganizationMemberRole.MEMBER)));

            ValidationException thrown = refused(List.of(grant(user, KalkanLevel.VIEW)));

            assertEquals("NOT_ACTIVE_MEMBER", thrown.getErrorValues().get("reason"));
        }

        @Test
        void guestAboveView_isRefusedWithTheLevel() {
            User guest = activeMember(OrganizationMemberRole.GUEST);

            ValidationException thrown = refused(List.of(grant(guest, KalkanLevel.EDIT)));

            assertEquals("KLK_VAL_003", thrown.getErrorCode());
            assertEquals("EDIT", thrown.getErrorValues().get("level"));
        }

        @Test
        void guestAtView_isAccepted() {
            User guest = activeMember(OrganizationMemberRole.GUEST);
            when(kalkanPagePermissionRepository.findByPage_IdOrderByCreatedAtAsc(root.getId())).thenReturn(List.of());

            service.replacePermissions(caller, organizationId, root.getId(), List.of(grant(guest, KalkanLevel.VIEW)));

            assertEquals(1, savedGrants().size());
        }

        @Test
        void invalidEntry_writesNothing() {
            User member = activeMember(OrganizationMemberRole.MEMBER);
            User guest = activeMember(OrganizationMemberRole.GUEST);

            refused(List.of(grant(member, KalkanLevel.EDIT), grant(guest, KalkanLevel.MANAGE)));

            verify(kalkanPagePermissionRepository, never()).softDeleteByIds(anyList(), any(), any());
            verify(kalkanPagePermissionRepository, never()).saveAllAndFlush(any());
        }
    }

    @Test
    void grantCreator_savesManageByTheCreator() {
        UUID creatorId = UUID.randomUUID();

        service.grantCreator(root, creatorId);

        ArgumentCaptor<KalkanPagePermission> captor = ArgumentCaptor.forClass(KalkanPagePermission.class);
        verify(kalkanPagePermissionRepository).save(captor.capture());
        assertEquals(KalkanLevel.MANAGE, captor.getValue().getLevel());
        assertEquals(creatorId, captor.getValue().getUserId());
        assertEquals(creatorId, captor.getValue().getCreatedById());
        assertEquals(root, captor.getValue().getPage());
    }
}
