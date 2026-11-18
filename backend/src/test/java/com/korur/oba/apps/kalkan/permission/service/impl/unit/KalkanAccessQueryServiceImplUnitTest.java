package com.korur.oba.apps.kalkan.permission.service.impl.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.mock_data.KalkanMocks;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.permission.dto.projection.KalkanRootLevelProjection;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.apps.kalkan.permission.repository.KalkanPagePermissionRepository;
import com.korur.oba.apps.kalkan.permission.service.impl.KalkanAccessQueryServiceImpl;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KalkanAccessQueryServiceImplUnitTest {

    @Mock
    private KalkanPagePermissionRepository kalkanPagePermissionRepository;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private KalkanAccessQueryServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final User user = KalkanMocks.user();

    private void roleIs(OrganizationMemberRole role) {
        when(organizationMemberQueryService.findMember(organizationId, user.getId()))
                .thenReturn(Optional.of(KalkanMocks.member(user, role)));
    }

    private void grantIs(UUID rootId, KalkanLevel level) {
        when(kalkanPagePermissionRepository.findLevel(rootId, user.getId())).thenReturn(Optional.ofNullable(level));
    }

    @Nested
    class ResolveLevel {

        @Test
        void noMembership_returnsNullWithoutReadingGrants() {
            UUID rootId = UUID.randomUUID();

            assertNull(service.resolveLevel(rootId, user, null));
            verify(kalkanPagePermissionRepository, never()).findLevel(any(), any());
        }

        @Test
        void owner_returnsManageWithoutAGrant() {
            assertEquals(KalkanLevel.MANAGE, service.resolveLevel(UUID.randomUUID(), user, OrganizationMemberRole.OWNER));
            verify(kalkanPagePermissionRepository, never()).findLevel(any(), any());
        }

        @Test
        void memberWithGrant_returnsTheGrantedLevel() {
            UUID rootId = UUID.randomUUID();
            grantIs(rootId, KalkanLevel.EDIT);

            assertEquals(KalkanLevel.EDIT, service.resolveLevel(rootId, user, OrganizationMemberRole.MEMBER));
        }

        @Test
        void adminWithoutGrant_returnsNull() {
            UUID rootId = UUID.randomUUID();
            grantIs(rootId, null);

            assertNull(service.resolveLevel(rootId, user, OrganizationMemberRole.ADMIN));
        }

        @Test
        void guestWithManageGrant_isCappedAtView() {
            UUID rootId = UUID.randomUUID();
            grantIs(rootId, KalkanLevel.MANAGE);

            assertEquals(KalkanLevel.VIEW, service.resolveLevel(rootId, user, OrganizationMemberRole.GUEST));
        }

        @Test
        void guestWithoutGrant_returnsNull() {
            UUID rootId = UUID.randomUUID();
            grantIs(rootId, null);

            assertNull(service.resolveLevel(rootId, user, OrganizationMemberRole.GUEST));
        }
    }

    @Nested
    class VerifyLevel {

        @Test
        void childPage_readsTheGrantOfItsRoot() {
            KalkanPage root = KalkanMocks.root(organizationId, "isms");
            KalkanPage child = KalkanMocks.child(root, "policies");
            roleIs(OrganizationMemberRole.MEMBER);
            grantIs(root.getId(), KalkanLevel.EDIT);

            assertEquals(KalkanLevel.EDIT, service.verifyLevel(organizationId, child, user, KalkanLevel.EDIT));
        }

        @Test
        void levelAboveMinimum_returnsTheActualLevel() {
            KalkanPage root = KalkanMocks.root(organizationId, "isms");
            roleIs(OrganizationMemberRole.MEMBER);
            grantIs(root.getId(), KalkanLevel.MANAGE);

            assertEquals(KalkanLevel.MANAGE, service.verifyLevel(organizationId, root, user, KalkanLevel.VIEW));
        }

        @Test
        void noLevel_throwsNotFoundWithPageId() {
            KalkanPage root = KalkanMocks.root(organizationId, "isms");
            roleIs(OrganizationMemberRole.MEMBER);
            grantIs(root.getId(), null);

            NotFoundException thrown = assertThrows(NotFoundException.class,
                    () -> service.verifyLevel(organizationId, root, user, KalkanLevel.VIEW));

            assertEquals("KLK_NOT_001", thrown.getErrorCode());
            assertEquals(Map.of("pageId", root.getId().toString()), thrown.getErrorValues());
        }

        @Test
        void noMembership_throwsNotFound() {
            KalkanPage root = KalkanMocks.root(organizationId, "isms");
            when(organizationMemberQueryService.findMember(organizationId, user.getId())).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> service.verifyLevel(organizationId, root, user, KalkanLevel.VIEW));
        }

        @Test
        void levelBelowMinimum_throwsForbiddenNamingTheMinimum() {
            KalkanPage root = KalkanMocks.root(organizationId, "isms");
            roleIs(OrganizationMemberRole.MEMBER);
            grantIs(root.getId(), KalkanLevel.EDIT);

            ForbiddenException thrown = assertThrows(ForbiddenException.class,
                    () -> service.verifyLevel(organizationId, root, user, KalkanLevel.MANAGE));

            assertEquals("KLK_SEC_001", thrown.getErrorCode());
            assertEquals("MANAGE", thrown.getErrorValues().get("level"));
        }

        @Test
        void guestAskingForEdit_isRefusedEvenWithAnEditGrant() {
            KalkanPage root = KalkanMocks.root(organizationId, "isms");
            roleIs(OrganizationMemberRole.GUEST);
            grantIs(root.getId(), KalkanLevel.EDIT);

            assertThrows(ForbiddenException.class,
                    () -> service.verifyLevel(organizationId, root, user, KalkanLevel.EDIT));
        }

        @Test
        void byIds_usesTheGivenRootId() {
            UUID rootId = UUID.randomUUID();
            roleIs(OrganizationMemberRole.MEMBER);
            grantIs(rootId, KalkanLevel.VIEW);

            assertEquals(KalkanLevel.VIEW,
                    service.verifyLevel(organizationId, UUID.randomUUID(), rootId, user, KalkanLevel.VIEW));
        }
    }

    @Nested
    class RequireRootCreate {

        @Test
        void admin_passes() {
            roleIs(OrganizationMemberRole.ADMIN);
            service.requireRootCreate(organizationId, user);
        }

        @Test
        void owner_passes() {
            roleIs(OrganizationMemberRole.OWNER);
            service.requireRootCreate(organizationId, user);
        }

        @Test
        void member_throwsForbiddenNamingTheAdminRole() {
            roleIs(OrganizationMemberRole.MEMBER);

            ForbiddenException thrown = assertThrows(ForbiddenException.class,
                    () -> service.requireRootCreate(organizationId, user));

            assertEquals("KLK_SEC_001", thrown.getErrorCode());
            assertEquals("ADMIN", thrown.getErrorValues().get("role"));
        }

        @Test
        void noMembership_throwsForbidden() {
            when(organizationMemberQueryService.findMember(organizationId, user.getId())).thenReturn(Optional.empty());

            assertThrows(ForbiddenException.class, () -> service.requireRootCreate(organizationId, user));
        }
    }

    @Nested
    class ResolveLevels {

        private KalkanRootLevelProjection row(UUID rootId, KalkanLevel level) {
            KalkanRootLevelProjection projection = mock(KalkanRootLevelProjection.class);
            when(projection.getRootId()).thenReturn(rootId);
            when(projection.getLevel()).thenReturn(level);
            return projection;
        }

        @Test
        void owner_getsManageOnEveryRootWithoutAQuery() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            roleIs(OrganizationMemberRole.OWNER);

            Map<UUID, KalkanLevel> levels = service.resolveLevels(organizationId, List.of(first, second), user);

            assertEquals(Map.of(first, KalkanLevel.MANAGE, second, KalkanLevel.MANAGE), levels);
            verify(kalkanPagePermissionRepository, never()).findLevels(any(), any());
        }

        @Test
        void member_getsOnlyRootsWithAGrant() {
            UUID granted = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            roleIs(OrganizationMemberRole.MEMBER);
            KalkanRootLevelProjection grantedRow = row(granted, KalkanLevel.EDIT);
            when(kalkanPagePermissionRepository.findLevels(List.of(granted, other), user.getId()))
                    .thenReturn(List.of(grantedRow));

            Map<UUID, KalkanLevel> levels = service.resolveLevels(organizationId, List.of(granted, other), user);

            assertEquals(Map.of(granted, KalkanLevel.EDIT), levels);
        }

        @Test
        void guest_isCappedAtView() {
            UUID granted = UUID.randomUUID();
            roleIs(OrganizationMemberRole.GUEST);
            KalkanRootLevelProjection grantedRow = row(granted, KalkanLevel.MANAGE);
            when(kalkanPagePermissionRepository.findLevels(List.of(granted), user.getId()))
                    .thenReturn(List.of(grantedRow));

            assertEquals(Map.of(granted, KalkanLevel.VIEW),
                    service.resolveLevels(organizationId, List.of(granted), user));
        }

        @Test
        void noMembership_returnsEmptyMap() {
            when(organizationMemberQueryService.findMember(organizationId, user.getId())).thenReturn(Optional.empty());

            assertTrue(service.resolveLevels(organizationId, List.of(UUID.randomUUID()), user).isEmpty());
        }

        @Test
        void noRoots_returnsEmptyMapWithoutAQuery() {
            roleIs(OrganizationMemberRole.MEMBER);

            assertTrue(service.resolveLevels(organizationId, List.of(), user).isEmpty());
            verify(kalkanPagePermissionRepository, never()).findLevels(any(), any());
        }
    }
}
