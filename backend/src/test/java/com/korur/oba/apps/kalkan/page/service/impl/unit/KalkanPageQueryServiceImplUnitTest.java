package com.korur.oba.apps.kalkan.page.service.impl.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.core.exception.enums.ExceptionDomainKalkanError;
import com.korur.oba.apps.kalkan.mock_data.KalkanMocks;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.page.mapper.KalkanPageMapper;
import com.korur.oba.apps.kalkan.page.mapper.KalkanPageMapperHelper;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageRepository;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageRevisionRepository;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageTranslationRepository;
import com.korur.oba.apps.kalkan.page.service.impl.KalkanPageQueryServiceImpl;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageTrashProjection;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageRecentResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageTrashResponse;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.apps.kalkan.permission.service.KalkanAccessQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KalkanPageQueryServiceImplUnitTest {

    @Mock
    private KalkanPageRepository kalkanPageRepository;
    @Mock
    private KalkanPageTranslationRepository kalkanPageTranslationRepository;
    @Mock
    private KalkanPageRevisionRepository kalkanPageRevisionRepository;
    @Mock
    private KalkanAccessQueryService kalkanAccessQueryService;
    @Mock
    private KalkanPageMapper kalkanPageMapper;
    @Mock
    private KalkanPageMapperHelper kalkanPageMapperHelper;
    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private KalkanPageQueryServiceImpl service;

    @Nested
    class GetEntityById {

        @Test
        void livePage_isReturnedWithoutAnOrganizationFilterOrLevelCheck() {
            // Arrange
            KalkanPage page = KalkanMocks.root(UUID.randomUUID(), "isms");
            when(kalkanPageRepository.findById(page.getId())).thenReturn(Optional.of(page));

            // Act
            KalkanPage found = service.getEntityById(page.getId());

            // Assert
            assertSame(page, found);
            verifyNoInteractions(kalkanAccessQueryService);
        }

        @Test
        void deletedOrMissingPage_throwsNotFound() {
            // Arrange
            UUID pageId = UUID.randomUUID();
            when(kalkanPageRepository.findById(pageId)).thenReturn(Optional.empty());

            // Act and Assert
            NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.getEntityById(pageId));
            assertEquals(ExceptionDomainKalkanError.NOT_PAGE_FOUND.getCode(), thrown.getErrorCode());
        }
    }

    @Nested
    class GetRevision {

        @Test
        void unknownRevision_throwsRevisionNotFound() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            User user = KalkanMocks.user();
            KalkanPage page = KalkanMocks.root(organizationId, "isms");
            UUID revisionId = UUID.randomUUID();
            when(kalkanPageRepository.findByIdAndOrganizationId(page.getId(), organizationId))
                    .thenReturn(Optional.of(page));
            when(kalkanPageRevisionRepository.findByIdAndPageId(revisionId, page.getId())).thenReturn(Optional.empty());

            // Act and Assert
            NotFoundException thrown = assertThrows(NotFoundException.class,
                    () -> service.getRevision(user, organizationId, page.getId(), revisionId));
            assertEquals(ExceptionDomainKalkanError.NOT_REVISION_FOUND.getCode(), thrown.getErrorCode());
            verify(kalkanAccessQueryService).verifyLevel(organizationId, page, user, KalkanLevel.VIEW);
        }

        @Test
        void levelTooLow_readsNoRevision() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            User user = KalkanMocks.user();
            KalkanPage page = KalkanMocks.root(organizationId, "isms");
            UUID revisionId = UUID.randomUUID();
            when(kalkanPageRepository.findByIdAndOrganizationId(page.getId(), organizationId))
                    .thenReturn(Optional.of(page));
            when(kalkanAccessQueryService.verifyLevel(organizationId, page, user, KalkanLevel.VIEW))
                    .thenThrow(new ForbiddenException(ExceptionDomainKalkanError.SEC_LEVEL_TOO_LOW, Map.of()));

            // Act and Assert
            assertThrows(ForbiddenException.class,
                    () -> service.getRevision(user, organizationId, page.getId(), revisionId));
            verifyNoInteractions(kalkanPageRevisionRepository);
        }
    }

    @Nested
    class GetTrash {

        private KalkanPageTrashProjection trashRow(UUID accessRootId, boolean root) {
            KalkanPageTrashProjection row = mock(KalkanPageTrashProjection.class);
            when(row.getAccessRootId()).thenReturn(accessRootId);
            when(row.getRoot()).thenReturn(root);
            return row;
        }

        @Test
        void rootsBelowEdit_areNotSearched() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            User user = KalkanMocks.user();
            UUID viewRootId = UUID.randomUUID();
            UUID editRootId = UUID.randomUUID();
            when(kalkanPageRepository.findRootIdsIncludingDeleted(organizationId))
                    .thenReturn(List.of(viewRootId, editRootId));
            when(kalkanAccessQueryService.resolveLevels(organizationId, List.of(viewRootId, editRootId), user))
                    .thenReturn(Map.of(viewRootId, KalkanLevel.VIEW, editRootId, KalkanLevel.EDIT));
            when(kalkanPageRepository.findTrash(organizationId, List.of(editRootId))).thenReturn(List.of());

            // Act
            List<KalkanPageTrashResponse> trash = service.getTrash(user, organizationId, null);

            // Assert
            assertTrue(trash.isEmpty());
            verify(kalkanPageRepository).findTrash(organizationId, List.of(editRootId));
        }

        @Test
        void editOnRoot_listsPageBelowButNotTheDeletedRoot() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            User user = KalkanMocks.user();
            UUID rootId = UUID.randomUUID();
            KalkanPageTrashProjection rootRow = trashRow(rootId, true);
            KalkanPageTrashProjection childRow = trashRow(rootId, false);
            KalkanPageTrashResponse childResponse = new KalkanPageTrashResponse(UUID.randomUUID(), null,
                    "isms/controls", Instant.now(), null, 0);
            when(kalkanPageRepository.findRootIdsIncludingDeleted(organizationId)).thenReturn(List.of(rootId));
            when(kalkanAccessQueryService.resolveLevels(organizationId, List.of(rootId), user))
                    .thenReturn(Map.of(rootId, KalkanLevel.EDIT));
            when(kalkanPageRepository.findTrash(organizationId, List.of(rootId)))
                    .thenReturn(List.of(rootRow, childRow));
            when(kalkanPageMapper.toTrashResponse(childRow, null, null)).thenReturn(childResponse);

            // Act
            List<KalkanPageTrashResponse> trash = service.getTrash(user, organizationId, null);

            // Assert
            assertEquals(List.of(childResponse), trash);
            verify(kalkanPageMapper, never()).toTrashResponse(rootRow, null, null);
        }

        @Test
        void manageOnRoot_listsTheDeletedRootAndPagesBelow() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            User user = KalkanMocks.user();
            UUID rootId = UUID.randomUUID();
            KalkanPageTrashProjection rootRow = trashRow(rootId, true);
            KalkanPageTrashProjection childRow = trashRow(rootId, false);
            KalkanPageTrashResponse rootResponse = new KalkanPageTrashResponse(rootId, null, "isms", Instant.now(),
                    null, 1);
            KalkanPageTrashResponse childResponse = new KalkanPageTrashResponse(UUID.randomUUID(), null,
                    "policies/controls", Instant.now(), null, 0);
            when(kalkanPageRepository.findRootIdsIncludingDeleted(organizationId)).thenReturn(List.of(rootId));
            when(kalkanAccessQueryService.resolveLevels(organizationId, List.of(rootId), user))
                    .thenReturn(Map.of(rootId, KalkanLevel.MANAGE));
            when(kalkanPageRepository.findTrash(organizationId, List.of(rootId)))
                    .thenReturn(List.of(rootRow, childRow));
            when(kalkanPageMapper.toTrashResponse(rootRow, null, null)).thenReturn(rootResponse);
            when(kalkanPageMapper.toTrashResponse(childRow, null, null)).thenReturn(childResponse);

            // Act
            List<KalkanPageTrashResponse> trash = service.getTrash(user, organizationId, null);

            // Assert
            assertEquals(List.of(rootResponse, childResponse), trash);
        }

        @Test
        void viewOnly_returnsEmptyWithoutQuery() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            User user = KalkanMocks.user();
            UUID rootId = UUID.randomUUID();
            when(kalkanPageRepository.findRootIdsIncludingDeleted(organizationId)).thenReturn(List.of(rootId));
            when(kalkanAccessQueryService.resolveLevels(organizationId, List.of(rootId), user))
                    .thenReturn(Map.of(rootId, KalkanLevel.VIEW));

            // Act
            List<KalkanPageTrashResponse> trash = service.getTrash(user, organizationId, null);

            // Assert
            assertTrue(trash.isEmpty());
            verify(kalkanPageRepository, never()).findTrash(any(), any());
        }
    }

    @Nested
    class GetRecent {

        @Test
        void noVisibleRoot_returnsEmptyWithoutQuery() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            User user = KalkanMocks.user();
            KalkanPage root = KalkanMocks.root(organizationId, "isms");
            when(kalkanPageRepository.findByOrganizationIdAndParentIsNull(organizationId)).thenReturn(List.of(root));
            when(kalkanAccessQueryService.resolveLevels(organizationId, List.of(root.getId()), user))
                    .thenReturn(Map.of());

            // Act
            List<KalkanPageRecentResponse> recent = service.getRecent(user, organizationId, 20, null);

            // Assert
            assertTrue(recent.isEmpty());
            verify(kalkanPageRepository, never()).findRecent(any(), any(), anyInt());
        }
    }
}
