package com.korur.oba.apps.kalkan.page.service.impl.unit;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.mock_data.KalkanMocks;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageStateProjection;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageMoveRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageTranslationRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageUpdateRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageUpsertRequest;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.page.entity.KalkanPageTranslation;
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;
import com.korur.oba.apps.kalkan.page.enums.KalkanUpsertOutcome;
import com.korur.oba.apps.kalkan.page.mapper.KalkanPageMapper;
import com.korur.oba.apps.kalkan.page.mapper.KalkanPageMapperHelper;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageRepository;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageRevisionRepository;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageTranslationRepository;
import com.korur.oba.apps.kalkan.page.service.KalkanPageQueryService;
import com.korur.oba.apps.kalkan.page.service.impl.KalkanPageServiceImpl;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.apps.kalkan.permission.service.KalkanAccessQueryService;
import com.korur.oba.apps.kalkan.permission.service.KalkanPermissionService;
import com.korur.oba.connect.azure.service.BlobUrlRewriter;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KalkanPageServiceImplUnitTest {

    @Mock
    private KalkanPageRepository kalkanPageRepository;
    @Mock
    private KalkanPageTranslationRepository kalkanPageTranslationRepository;
    @Mock
    private KalkanPageRevisionRepository kalkanPageRevisionRepository;
    @Mock
    private KalkanPageQueryService kalkanPageQueryService;
    @Mock
    private KalkanAccessQueryService kalkanAccessQueryService;
    @Mock
    private KalkanPermissionService kalkanPermissionService;
    @Mock
    private KalkanPageMapper kalkanPageMapper;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private BlobUrlRewriter blobUrlRewriter;
    @Mock
    private LoggingService loggingService;

    private static final Instant NOW = Instant.parse("2026-11-06T09:00:00Z");

    private KalkanPageServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final User user = KalkanMocks.user();

    @BeforeEach
    void setUp() {
        service = new KalkanPageServiceImpl(kalkanPageRepository, kalkanPageTranslationRepository,
                kalkanPageRevisionRepository, kalkanPageQueryService, kalkanAccessQueryService,
                kalkanPermissionService, kalkanPageMapper, new KalkanPageMapperHelper(userQueryService),
                blobUrlRewriter, loggingService, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(kalkanPageRepository.saveAndFlush(any(KalkanPage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(blobUrlRewriter.stripMarkdownBlobUrlSignatures(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static KalkanPageTranslationRequest text(KalkanLocale locale, String title, String body) {
        return new KalkanPageTranslationRequest(locale, title, body);
    }

    private KalkanPageTranslation stored(KalkanPage page, KalkanLocale locale, String title, String body) {
        KalkanPageTranslation translation = new KalkanPageTranslation();
        translation.setPage(page);
        translation.setLocale(locale);
        translation.setTitle(title);
        translation.setBody(body);
        return translation;
    }

    /** A chain of pages below the root, one per slug; the last one is the deepest. */
    private List<KalkanPage> chain(KalkanPage root, int length) {
        List<KalkanPage> pages = new ArrayList<>();
        KalkanPage parent = root;
        for (int i = 0; i < length; i++) {
            parent = KalkanMocks.child(parent, "p" + i);
            pages.add(parent);
        }
        return pages;
    }

    @Nested
    class UpsertOutcome {

        private final KalkanPage page = KalkanMocks.root(organizationId, "docs");

        private void pageExists(Long pushedVersion, long version, String title, String body) {
            page.setVersion(version);
            page.setPushedVersion(pushedVersion);
            page.setUpdatedById(user.getId());
            page.setUpdatedAt(Instant.parse("2026-11-05T10:00:00Z"));
            when(kalkanPageRepository.findByOrganizationIdAndParentIsNullAndSlug(organizationId, "docs"))
                    .thenReturn(Optional.of(page));
            when(kalkanPageTranslationRepository.findByPage_IdOrderByLocaleAsc(page.getId()))
                    .thenReturn(List.of(stored(page, KalkanLocale.EN, title, body)));
        }

        private KalkanPageUpsertRequest push(String title, boolean force) {
            return new KalkanPageUpsertRequest("docs", null, null, List.of(text(KalkanLocale.EN, title, "body")), force);
        }

        private KalkanUpsertOutcome outcomeOf() {
            ArgumentCaptor<KalkanUpsertOutcome> captor = ArgumentCaptor.forClass(KalkanUpsertOutcome.class);
            verify(kalkanPageMapper).toUpsertResponse(any(), eq("docs"), captor.capture(), anyLong());
            return captor.getValue();
        }

        @Test
        void upsertByPath_missingRoot_createsAfterRootCheck() {
            when(kalkanPageRepository.findByOrganizationIdAndParentIsNullAndSlug(organizationId, "docs"))
                    .thenReturn(Optional.empty());
            when(kalkanPageMapper.toTranslationEntity(any())).thenReturn(new KalkanPageTranslation());

            service.upsertByPath(user, organizationId, push("T", false), false);

            verify(kalkanAccessQueryService).requireRootCreate(organizationId, user);
            verify(kalkanPermissionService).grantCreator(any(KalkanPage.class), eq(user.getId()));
            verify(kalkanPageRepository).markPushed(any());
            assertEquals(KalkanUpsertOutcome.CREATED, outcomeOf());
        }

        @Test
        void upsertByPath_bodyWithSignedBlobUrls_storesTheUnsignedBody() {
            when(kalkanPageRepository.findByOrganizationIdAndParentIsNullAndSlug(organizationId, "docs"))
                    .thenReturn(Optional.empty());
            when(blobUrlRewriter.stripMarkdownBlobUrlSignatures("![x](blob?sig=1)")).thenReturn("![x](blob)");
            when(kalkanPageMapper.toTranslationEntity(any())).thenReturn(new KalkanPageTranslation());
            KalkanPageUpsertRequest signed = new KalkanPageUpsertRequest("docs", null, null,
                    List.of(text(KalkanLocale.EN, "T", "![x](blob?sig=1)")), false);

            service.upsertByPath(user, organizationId, signed, false);

            ArgumentCaptor<KalkanPageTranslationRequest> stored =
                    ArgumentCaptor.forClass(KalkanPageTranslationRequest.class);
            verify(kalkanPageMapper).toTranslationEntity(stored.capture());
            assertEquals("![x](blob)", stored.getValue().body());
        }

        @Test
        void upsertByPath_sameContentButSignedBlobUrls_isUnchanged() {
            pageExists(3L, 3, "T", "![x](blob)");
            when(blobUrlRewriter.stripMarkdownBlobUrlSignatures("![x](blob?sig=1)")).thenReturn("![x](blob)");
            KalkanPageUpsertRequest signed = new KalkanPageUpsertRequest("docs", null, null,
                    List.of(text(KalkanLocale.EN, "T", "![x](blob?sig=1)")), false);

            service.upsertByPath(user, organizationId, signed, false);

            verify(kalkanPageRepository, never()).saveAndFlush(any());
            verify(kalkanPageRevisionRepository, never()).saveAll(any());
            assertEquals(KalkanUpsertOutcome.UNCHANGED, outcomeOf());
        }

        @Test
        void upsertByPath_missingRootDryRun_writesNothing() {
            when(kalkanPageRepository.findByOrganizationIdAndParentIsNullAndSlug(organizationId, "docs"))
                    .thenReturn(Optional.empty());

            service.upsertByPath(user, organizationId, push("T", false), true);

            verify(kalkanPageRepository, never()).saveAndFlush(any());
            verify(kalkanPermissionService, never()).grantCreator(any(), any());
            assertEquals(KalkanUpsertOutcome.CREATED, outcomeOf());
        }

        @Test
        void upsertByPath_sameContentWithNullPushedVersion_isUnchangedNotAConflict() {
            pageExists(null, 3, "T", "body");

            service.upsertByPath(user, organizationId, push("T", false), false);

            verify(kalkanPageRepository, never()).saveAndFlush(any());
            verify(kalkanPageRepository, never()).markPushed(any());
            assertEquals(KalkanUpsertOutcome.UNCHANGED, outcomeOf());
        }

        @Test
        void upsertByPath_changedContentPushedVersionNull_throwsChangedSincePush() {
            pageExists(null, 1, "Old", "body");

            ConflictException thrown = assertThrows(ConflictException.class,
                    () -> service.upsertByPath(user, organizationId, push("New", false), false));

            assertEquals("KLK_STA_004", thrown.getErrorCode());
            assertEquals("docs", thrown.getErrorValues().get("path"));
            verify(kalkanPageRepository, never()).saveAndFlush(any());
        }

        @Test
        void upsertByPath_pushedVersionOlderThanVersion_throwsChangedSincePush() {
            pageExists(2L, 3, "Old", "body");

            ConflictException thrown = assertThrows(ConflictException.class,
                    () -> service.upsertByPath(user, organizationId, push("New", false), false));

            assertEquals("KLK_STA_004", thrown.getErrorCode());
            assertEquals(user.getId().toString(), thrown.getErrorValues().get("updatedById"));
        }

        @Test
        void upsertByPath_pushedVersionEqualsVersion_updatesAndMarksPushed() {
            pageExists(3L, 3, "Old", "body");

            service.upsertByPath(user, organizationId, push("New", false), false);

            verify(kalkanPageRepository).saveAndFlush(page);
            verify(kalkanPageRepository).markPushed(page.getId());
            assertEquals(KalkanUpsertOutcome.UPDATED, outcomeOf());
        }

        @Test
        void upsertByPath_changedSincePushWithForce_updates() {
            pageExists(1L, 3, "Old", "body");

            service.upsertByPath(user, organizationId, push("New", true), false);

            verify(kalkanPageRepository).markPushed(page.getId());
            assertEquals(KalkanUpsertOutcome.UPDATED, outcomeOf());
        }

        @Test
        void upsertByPath_changedContentDryRun_answersUpdatedAndWritesNothing() {
            pageExists(3L, 3, "Old", "body");

            service.upsertByPath(user, organizationId, push("New", false), true);

            verify(kalkanPageRepository, never()).saveAndFlush(any());
            verify(kalkanPageRepository, never()).markPushed(any());
            assertEquals(KalkanUpsertOutcome.UPDATED, outcomeOf());
        }

        @Test
        void upsertByPath_changedContentDryRunWithoutForce_stillReportsTheConflict() {
            pageExists(1L, 3, "Old", "body");

            assertThrows(ConflictException.class,
                    () -> service.upsertByPath(user, organizationId, push("New", false), true));
        }

        @Test
        void upsertByPath_missingIntermediatePage_throwsParentPathMissing() {
            KalkanPageUpsertRequest request = new KalkanPageUpsertRequest("docs/a/b", null, null,
                    List.of(text(KalkanLocale.EN, "T", "b")), false);
            when(kalkanPageRepository.findByOrganizationIdAndParentIsNullAndSlug(organizationId, "docs"))
                    .thenReturn(Optional.of(page));
            when(kalkanPageRepository.findByParent_IdAndSlug(page.getId(), "a")).thenReturn(Optional.empty());

            ValidationException thrown = assertThrows(ValidationException.class,
                    () -> service.upsertByPath(user, organizationId, request, false));

            assertEquals("KLK_VAL_001", thrown.getErrorCode());
        }

        @Test
        void upsertByPath_reservedRootSlug_isRefused() {
            KalkanPageUpsertRequest request = new KalkanPageUpsertRequest("api", null, null,
                    List.of(text(KalkanLocale.EN, "T", "b")), false);
            when(kalkanPageRepository.findByOrganizationIdAndParentIsNullAndSlug(organizationId, "api"))
                    .thenReturn(Optional.empty());

            ValidationException thrown = assertThrows(ValidationException.class,
                    () -> service.upsertByPath(user, organizationId, request, false));

            assertEquals("KLK_VAL_007", thrown.getErrorCode());
        }

        @Test
        void upsertByPath_duplicateLocale_isRefused() {
            KalkanPageUpsertRequest request = new KalkanPageUpsertRequest("docs", null, null,
                    List.of(text(KalkanLocale.EN, "A", "a"), text(KalkanLocale.EN, "B", "b")), false);

            ValidationException thrown = assertThrows(ValidationException.class,
                    () -> service.upsertByPath(user, organizationId, request, false));

            assertEquals("KLK_VAL_005", thrown.getErrorCode());
        }
    }

    @Nested
    class MovePage {

        private final KalkanPage root = KalkanMocks.root(organizationId, "isms");

        private ValidationException refusedMove(KalkanPage page, UUID parentId) {
            return assertThrows(ValidationException.class, () -> service.movePage(user, organizationId, page.getId(),
                    new KalkanPageMoveRequest(parentId, 0, page.getVersion())));
        }

        private void found(KalkanPage... pages) {
            for (KalkanPage page : pages) {
                when(kalkanPageQueryService.getEntity(organizationId, page.getId())).thenReturn(page);
            }
            when(kalkanAccessQueryService.verifyLevel(eq(organizationId), any(KalkanPage.class), eq(user),
                    eq(KalkanLevel.EDIT))).thenReturn(KalkanLevel.EDIT);
        }

        @Test
        void movePage_intoOwnChild_throwsOwnSubtree() {
            KalkanPage a = KalkanMocks.child(root, "a");
            KalkanPage b = KalkanMocks.child(a, "b");
            found(a, b);

            ValidationException thrown = refusedMove(a, b.getId());

            assertEquals("KLK_VAL_004", thrown.getErrorCode());
            assertEquals("OWN_SUBTREE", thrown.getErrorValues().get("reason"));
        }

        @Test
        void movePage_intoItself_throwsOwnSubtree() {
            KalkanPage a = KalkanMocks.child(root, "a");
            found(a);

            assertEquals("OWN_SUBTREE", refusedMove(a, a.getId()).getErrorValues().get("reason"));
        }

        @Test
        void movePage_rootUnderAPage_throwsRootUnderPage() {
            KalkanPage other = KalkanMocks.root(organizationId, "other");
            found(root);

            assertEquals("ROOT_UNDER_PAGE", refusedMove(root, other.getId()).getErrorValues().get("reason"));
        }

        @Test
        void movePage_childToTopLevel_throwsPageToTop() {
            KalkanPage a = KalkanMocks.child(root, "a");
            found(a);

            assertEquals("PAGE_TO_TOP", refusedMove(a, null).getErrorValues().get("reason"));
        }

        @Test
        void movePage_resultingDepthAboveTen_throwsTooDeepWithMaxDepth() {
            KalkanPage moved = KalkanMocks.child(KalkanMocks.root(organizationId, "other"), "moved");
            List<KalkanPage> deep = chain(root, 8);
            KalkanPage target = deep.get(7);
            found(moved, target);
            when(kalkanPageRepository.findSubtreeHeight(moved.getId())).thenReturn(2);

            ValidationException thrown = refusedMove(moved, target.getId());

            assertEquals("TOO_DEEP", thrown.getErrorValues().get("reason"));
            assertEquals(10, thrown.getErrorValues().get("maxDepth"));
        }

        @Test
        void movePage_resultingDepthExactlyTen_isAllowed() {
            KalkanPage moved = KalkanMocks.child(KalkanMocks.root(organizationId, "other"), "moved");
            List<KalkanPage> deep = chain(root, 8);
            KalkanPage target = deep.get(7);
            found(moved, target);
            when(kalkanPageRepository.findSubtreeHeight(moved.getId())).thenReturn(1);

            service.movePage(user, organizationId, moved.getId(), new KalkanPageMoveRequest(target.getId(), 0, 0L));

            verify(kalkanPageRepository).saveAndFlush(moved);
            verify(kalkanPageRepository).updateDescendantRoot(moved.getId(), root.getId());
        }

        @Test
        void movePage_slugTakenAtTarget_throwsSlugTaken() {
            KalkanPage moved = KalkanMocks.child(root, "moved");
            KalkanPage target = KalkanMocks.child(root, "target");
            found(moved, target);
            when(kalkanPageRepository.findSubtreeHeight(moved.getId())).thenReturn(1);
            when(kalkanPageRepository.existsChildSlug(target.getId(), "moved", moved.getId())).thenReturn(true);

            ConflictException thrown = assertThrows(ConflictException.class, () -> service.movePage(user,
                    organizationId, moved.getId(), new KalkanPageMoveRequest(target.getId(), 0, 0L)));

            assertEquals("KLK_STA_001", thrown.getErrorCode());
            verify(kalkanPageRepository, never()).saveAndFlush(any());
        }

        @Test
        void movePage_withinSameRoot_doesNotRewriteDescendantRoots() {
            KalkanPage moved = KalkanMocks.child(root, "moved");
            KalkanPage target = KalkanMocks.child(root, "target");
            found(moved, target);
            when(kalkanPageRepository.findSubtreeHeight(moved.getId())).thenReturn(1);

            service.movePage(user, organizationId, moved.getId(), new KalkanPageMoveRequest(target.getId(), 3, 0L));

            verify(kalkanPageRepository, never()).updateDescendantRoot(any(), any());
            assertEquals(3, moved.getPosition());
        }

        @Test
        void movePage_staleVersion_throwsVersionOutdatedBeforeAnyWrite() {
            KalkanPage a = KalkanMocks.child(root, "a");
            a.setVersion(4);
            found(a);

            ConflictException thrown = assertThrows(ConflictException.class, () -> service.movePage(user,
                    organizationId, a.getId(), new KalkanPageMoveRequest(root.getId(), 0, 3L)));

            assertEquals("KLK_STA_002", thrown.getErrorCode());
            verify(kalkanPageRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class SlugUniqueness {

        private final KalkanPage root = KalkanMocks.root(organizationId, "isms");

        @Test
        void createPage_rootSlugTaken_throwsSlugTaken() {
            when(kalkanPageRepository.existsRootSlug(organizationId, "isms", null)).thenReturn(true);

            ConflictException thrown = assertThrows(ConflictException.class, () -> service.createPage(user,
                    organizationId, new com.korur.oba.apps.kalkan.page.dto.request.KalkanPageCreateRequest(null, "isms",
                            null, false, List.of(text(KalkanLocale.EN, "T", "b")))));

            assertEquals("KLK_STA_001", thrown.getErrorCode());
            verify(kalkanPageRepository, never()).saveAndFlush(any());
        }

        @Test
        void createPage_childSlugTaken_throwsSlugTaken() {
            when(kalkanPageQueryService.getEntity(organizationId, root.getId())).thenReturn(root);
            when(kalkanAccessQueryService.verifyLevel(organizationId, root, user, KalkanLevel.EDIT))
                    .thenReturn(KalkanLevel.EDIT);
            when(kalkanPageRepository.existsChildSlug(root.getId(), "a", null)).thenReturn(true);

            assertThrows(ConflictException.class, () -> service.createPage(user, organizationId,
                    new com.korur.oba.apps.kalkan.page.dto.request.KalkanPageCreateRequest(root.getId(), "a", null,
                            false, List.of(text(KalkanLocale.EN, "T", "b")))));
        }

        @Test
        void createPage_childBeyondMaxDepth_throwsTooDeep() {
            KalkanPage deepest = chain(root, 9).get(8);
            when(kalkanPageQueryService.getEntity(organizationId, deepest.getId())).thenReturn(deepest);
            when(kalkanAccessQueryService.verifyLevel(organizationId, deepest, user, KalkanLevel.EDIT))
                    .thenReturn(KalkanLevel.EDIT);

            ValidationException thrown = assertThrows(ValidationException.class, () -> service.createPage(user,
                    organizationId, new com.korur.oba.apps.kalkan.page.dto.request.KalkanPageCreateRequest(
                            deepest.getId(), "x", null, false, List.of(text(KalkanLocale.EN, "T", "b")))));

            assertEquals("TOO_DEEP", thrown.getErrorValues().get("reason"));
        }

        @Test
        void createPage_rootBelowAdmin_isRefusedBeforeTheSlugIsLooked() {
            org.mockito.Mockito.doThrow(new com.korur.oba.dojo.exception.internal.auth.ForbiddenException())
                    .when(kalkanAccessQueryService).requireRootCreate(organizationId, user);

            assertThrows(com.korur.oba.dojo.exception.internal.auth.ForbiddenException.class,
                    () -> service.createPage(user, organizationId,
                            new com.korur.oba.apps.kalkan.page.dto.request.KalkanPageCreateRequest(null, "isms", null,
                                    false, List.of(text(KalkanLocale.EN, "T", "b")))));
            verify(kalkanPageRepository, never()).existsRootSlug(any(), any(), any());
        }

        @Test
        void updatePage_renameToLiveSiblingSlug_throwsSlugTakenExcludingItself() {
            KalkanPage page = KalkanMocks.child(root, "a");
            when(kalkanPageQueryService.getEntity(organizationId, page.getId())).thenReturn(page);
            when(kalkanAccessQueryService.verifyLevel(organizationId, page, user, KalkanLevel.EDIT))
                    .thenReturn(KalkanLevel.EDIT);
            when(kalkanPageRepository.existsChildSlug(root.getId(), "b", page.getId())).thenReturn(true);

            assertThrows(ConflictException.class, () -> service.updatePage(user, organizationId, page.getId(),
                    new KalkanPageUpdateRequest("b", null, 0L, null)));
            verify(kalkanPageRepository, never()).saveAndFlush(any());
        }

        @Test
        void updatePage_rootRenamedToReservedSlug_isRefused() {
            when(kalkanPageQueryService.getEntity(organizationId, root.getId())).thenReturn(root);
            when(kalkanAccessQueryService.verifyLevel(organizationId, root, user, KalkanLevel.EDIT))
                    .thenReturn(KalkanLevel.EDIT);

            ValidationException thrown = assertThrows(ValidationException.class, () -> service.updatePage(user,
                    organizationId, root.getId(), new KalkanPageUpdateRequest("onboarding", null, 0L, null)));

            assertEquals("KLK_VAL_007", thrown.getErrorCode());
        }

        @Test
        void updatePage_sameSlug_doesNotCheckUniqueness() {
            KalkanPage page = KalkanMocks.child(root, "a");
            when(kalkanPageQueryService.getEntity(organizationId, page.getId())).thenReturn(page);
            when(kalkanAccessQueryService.verifyLevel(organizationId, page, user, KalkanLevel.EDIT))
                    .thenReturn(KalkanLevel.EDIT);

            service.updatePage(user, organizationId, page.getId(), new KalkanPageUpdateRequest("a", null, 0L, null));

            verify(kalkanPageRepository, never()).existsChildSlug(any(), any(), any());
            verify(kalkanPageRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class RestorePage {

        private KalkanPageStateProjection state(UUID id, UUID parentId, UUID rootId, String slug, boolean deleted,
                                                boolean parentDeleted) {
            KalkanPageStateProjection state = mock(KalkanPageStateProjection.class);
            lenient().when(state.getId()).thenReturn(id);
            lenient().when(state.getParentId()).thenReturn(parentId);
            lenient().when(state.getRootId()).thenReturn(rootId);
            lenient().when(state.getSlug()).thenReturn(slug);
            lenient().when(state.getDeleted()).thenReturn(deleted);
            lenient().when(state.getParentDeleted()).thenReturn(parentDeleted);
            return state;
        }

        @Test
        void restorePage_rootNeedsManage_childNeedsEdit() {
            UUID rootId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            KalkanPageStateProjection rootState = state(rootId, null, null, "isms", false, false);
            KalkanPageStateProjection childState = state(childId, rootId, rootId, "a", false, false);
            when(kalkanPageRepository.findStateIncludingDeleted(rootId, organizationId)).thenReturn(Optional.of(rootState));
            when(kalkanPageRepository.findStateIncludingDeleted(childId, organizationId)).thenReturn(Optional.of(childState));
            when(kalkanAccessQueryService.verifyLevel(organizationId, rootId, rootId, user, KalkanLevel.MANAGE))
                    .thenReturn(KalkanLevel.MANAGE);
            when(kalkanAccessQueryService.verifyLevel(organizationId, childId, rootId, user, KalkanLevel.EDIT))
                    .thenReturn(KalkanLevel.EDIT);

            assertThrows(ValidationException.class, () -> service.restorePage(user, organizationId, rootId));
            assertThrows(ValidationException.class, () -> service.restorePage(user, organizationId, childId));

            verify(kalkanAccessQueryService).verifyLevel(organizationId, rootId, rootId, user, KalkanLevel.MANAGE);
            verify(kalkanAccessQueryService).verifyLevel(organizationId, childId, rootId, user, KalkanLevel.EDIT);
        }

        @Test
        void restorePage_livePage_throwsNotDeletedAfterTheLevelCheck() {
            UUID rootId = UUID.randomUUID();
            KalkanPageStateProjection liveState = state(rootId, null, null, "isms", false, false);
            when(kalkanPageRepository.findStateIncludingDeleted(rootId, organizationId))
                    .thenReturn(Optional.of(liveState));

            ValidationException thrown = assertThrows(ValidationException.class,
                    () -> service.restorePage(user, organizationId, rootId));

            assertEquals("KLK_VAL_008", thrown.getErrorCode());
            verify(kalkanAccessQueryService).verifyLevel(organizationId, rootId, rootId, user, KalkanLevel.MANAGE);
        }

        @Test
        void restorePage_parentDeleted_throwsParentDeleted() {
            UUID rootId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            KalkanPageStateProjection deletedState = state(childId, rootId, rootId, "a", true, true);
            when(kalkanPageRepository.findStateIncludingDeleted(childId, organizationId))
                    .thenReturn(Optional.of(deletedState));

            ConflictException thrown = assertThrows(ConflictException.class,
                    () -> service.restorePage(user, organizationId, childId));

            assertEquals("KLK_STA_003", thrown.getErrorCode());
            verify(kalkanPageRepository, never()).restoreSubtree(any(), any(), any());
        }

        @Test
        void restorePage_rootSlugTaken_throwsSlugTaken() {
            UUID rootId = UUID.randomUUID();
            KalkanPageStateProjection deletedState = state(rootId, null, null, "isms", true, false);
            when(kalkanPageRepository.findStateIncludingDeleted(rootId, organizationId))
                    .thenReturn(Optional.of(deletedState));
            when(kalkanPageRepository.existsRootSlug(organizationId, "isms", null)).thenReturn(true);

            assertEquals("KLK_STA_001", assertThrows(ConflictException.class,
                    () -> service.restorePage(user, organizationId, rootId)).getErrorCode());
        }

        @Test
        void restorePage_childSlugTaken_throwsSlugTaken() {
            UUID rootId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            KalkanPageStateProjection deletedState = state(childId, rootId, rootId, "a", true, false);
            when(kalkanPageRepository.findStateIncludingDeleted(childId, organizationId))
                    .thenReturn(Optional.of(deletedState));
            when(kalkanPageRepository.existsChildSlug(rootId, "a", null)).thenReturn(true);

            assertThrows(ConflictException.class, () -> service.restorePage(user, organizationId, childId));
            verify(kalkanPageRepository, never()).restoreSubtree(any(), any(), any());
        }

        @Test
        void restorePage_subtreeWouldExceedMaxDepth_throwsTooDeepBeforeAnyWrite() {
            UUID rootId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            KalkanPageStateProjection deletedState = state(childId, rootId, rootId, "b", true, false);
            when(kalkanPageRepository.findStateIncludingDeleted(childId, organizationId))
                    .thenReturn(Optional.of(deletedState));
            when(kalkanPageRepository.findRestoredDepth(childId)).thenReturn(11);

            ValidationException thrown = assertThrows(ValidationException.class,
                    () -> service.restorePage(user, organizationId, childId));

            assertEquals("KLK_VAL_004", thrown.getErrorCode());
            assertEquals("TOO_DEEP", thrown.getErrorValues().get("reason"));
            assertEquals(10, thrown.getErrorValues().get("maxDepth"));
            verify(kalkanPageRepository, never()).restoreSubtree(any(), any(), any());
            verify(kalkanPageTranslationRepository, never()).restoreBySubtree(any(), any(), any());
        }

        @Test
        void restorePage_subtreeExactlyAtMaxDepth_isRestored() {
            UUID rootId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            KalkanPageStateProjection deletedState = state(childId, rootId, rootId, "b", true, false);
            when(kalkanPageRepository.findStateIncludingDeleted(childId, organizationId))
                    .thenReturn(Optional.of(deletedState));
            when(kalkanPageRepository.findRestoredDepth(childId)).thenReturn(10);

            service.restorePage(user, organizationId, childId);

            verify(kalkanPageRepository).restoreSubtree(eq(childId), eq(user.getId()), any());
        }

        @Test
        void restorePage_unknownPage_throwsNotFound() {
            UUID pageId = UUID.randomUUID();
            when(kalkanPageRepository.findStateIncludingDeleted(pageId, organizationId)).thenReturn(Optional.empty());

            assertEquals("KLK_NOT_001", assertThrows(
                    com.korur.oba.dojo.exception.internal.business.NotFoundException.class,
                    () -> service.restorePage(user, organizationId, pageId)).getErrorCode());
            verify(kalkanAccessQueryService, never()).verifyLevel(any(), any(UUID.class), any(), any(), any());
        }
    }
}
