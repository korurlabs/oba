package com.korur.oba.apps.kalkan.page.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.core.exception.enums.ExceptionDomainKalkanError;
import com.korur.oba.apps.kalkan.page.constant.KalkanPageConstant;
import com.korur.oba.apps.kalkan.page.constant.service.KalkanPageServiceLogConstant;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageStateProjection;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageCreateRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageMoveRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageTranslationRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageUpdateRequest;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageUpsertRequest;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageDetailResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageUpsertResponse;
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
import com.korur.oba.apps.kalkan.page.service.KalkanPageService;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.apps.kalkan.permission.service.KalkanAccessQueryService;
import com.korur.oba.apps.kalkan.permission.service.KalkanPermissionService;
import com.korur.oba.connect.azure.service.BlobUrlRewriter;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * The {@link KalkanPageService} implementation.
 *
 * The page's version is bumped by Hibernate's optimistic lock whenever the page row is written, so
 * every write that changes only translations still touches the page row. All reads of a write run
 * before the page is changed: a query flushes a changed page and would bump its version a second
 * time.
 */
@Service
@RequiredArgsConstructor
public class KalkanPageServiceImpl implements KalkanPageService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(KalkanPageServiceImpl.class);

    private final KalkanPageRepository kalkanPageRepository;
    private final KalkanPageTranslationRepository kalkanPageTranslationRepository;
    private final KalkanPageRevisionRepository kalkanPageRevisionRepository;
    private final KalkanPageQueryService kalkanPageQueryService;
    private final KalkanAccessQueryService kalkanAccessQueryService;
    private final KalkanPermissionService kalkanPermissionService;
    private final KalkanPageMapper kalkanPageMapper;
    private final KalkanPageMapperHelper kalkanPageMapperHelper;
    private final BlobUrlRewriter blobUrlRewriter;
    private final LoggingService loggingService;
    private final Clock clock;

    @Override
    @Transactional
    public KalkanPageDetailResponse createPage(User user, UUID organizationId, KalkanPageCreateRequest request) {
        requireDistinctLocales(organizationId, request.translations());
        KalkanPage parent = null;
        KalkanLevel level;
        if (request.parentId() == null) {
            kalkanAccessQueryService.requireRootCreate(organizationId, user);
            requireNotReservedRootSlug(organizationId, request.slug());
            requireRootSlugNotInTrash(organizationId, request.slug());
            // The creator is granted MANAGE below; an OWNER holds it anyway.
            level = KalkanLevel.MANAGE;
        } else {
            parent = kalkanPageQueryService.getEntity(organizationId, request.parentId());
            level = kalkanAccessQueryService.verifyLevel(organizationId, parent, user, KalkanLevel.EDIT);
            requireDepthForChild(organizationId, parent, request.slug());
        }
        requireSlugFree(organizationId, parent, request.slug(), null);
        int position = request.position() != null ? request.position() : nextPosition(organizationId, parent);

        KalkanPage page = insertPage(organizationId, parent, request.slug(), position, request.compliance(),
                unsigned(request.translations()), user.getId());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PAGE_CREATED,
                page.getId(), request.parentId(), organizationId, user.getId());
        return kalkanPageQueryService.toDetailResponse(page, level);
    }

    @Override
    @Transactional
    public KalkanPageDetailResponse updatePage(User user, UUID organizationId, UUID pageId,
                                               KalkanPageUpdateRequest request) {
        KalkanPage page = kalkanPageQueryService.getEntity(organizationId, pageId);
        KalkanLevel level = kalkanAccessQueryService.verifyLevel(organizationId, page, user, KalkanLevel.EDIT);
        requireVersion(organizationId, page, request.version());
        List<KalkanPageTranslationRequest> translations = request.translations() == null
                ? List.of() : unsigned(request.translations());
        requireDistinctLocales(organizationId, translations);

        boolean slugChanged = request.slug() != null && !request.slug().equals(page.getSlug());
        if (slugChanged) {
            requireSlugChangeBelowRoot(organizationId, page, request.slug());
            requireSlugFree(organizationId, page.getParent(), request.slug(), page.getId());
        }
        boolean complianceChanged = request.compliance() != null && request.compliance() != page.isCompliance();
        Map<KalkanLocale, KalkanPageTranslation> stored = storedTranslations(page);

        // Every read is done; from here on the page and its translations change.
        if (slugChanged) {
            page.setSlug(request.slug());
        }
        if (complianceChanged) {
            page.setCompliance(request.compliance());
        }
        List<KalkanPageTranslation> changed = applyTranslations(page, stored, translations, user.getId());
        if (slugChanged || complianceChanged || !changed.isEmpty()) {
            writePage(page, changed, user.getId());
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PAGE_UPDATED,
                page.getId(), organizationId, user.getId(), page.getVersion(), changed.size());
        return kalkanPageQueryService.toDetailResponse(page, level);
    }

    @Override
    @Transactional
    public KalkanPageDetailResponse movePage(User user, UUID organizationId, UUID pageId,
                                             KalkanPageMoveRequest request) {
        KalkanPage page = kalkanPageQueryService.getEntity(organizationId, pageId);
        KalkanLevel level = kalkanAccessQueryService.verifyLevel(organizationId, page, user, KalkanLevel.EDIT);
        requireVersion(organizationId, page, request.version());

        KalkanPage target = null;
        if (page.getParent() == null) {
            // A root stays a root: its permissions would otherwise govern a page inside another tree.
            if (request.parentId() != null) {
                throw invalidMove(organizationId, page, request.parentId(), KalkanPageConstant.REASON_ROOT_UNDER_PAGE);
            }
        } else {
            // Only an ADMIN creates a root, so a page cannot become one by a move.
            if (request.parentId() == null) {
                throw invalidMove(organizationId, page, null, KalkanPageConstant.REASON_PAGE_TO_TOP);
            }
            target = kalkanPageQueryService.getEntity(organizationId, request.parentId());
            kalkanAccessQueryService.verifyLevel(organizationId, target, user, KalkanLevel.EDIT);
            for (KalkanPage ancestor = target; ancestor != null; ancestor = ancestor.getParent()) {
                if (ancestor.getId().equals(page.getId())) {
                    throw invalidMove(organizationId, page, target.getId(), KalkanPageConstant.REASON_OWN_SUBTREE);
                }
            }
            int depth = kalkanPageMapperHelper.depthOf(target) + kalkanPageRepository.findSubtreeHeight(page.getId());
            if (depth > KalkanPageConstant.MAX_DEPTH) {
                throw invalidMove(organizationId, page, target.getId(), KalkanPageConstant.REASON_TOO_DEEP);
            }
            requireSlugFree(organizationId, target, page.getSlug(), page.getId());
        }

        // Every read is done; from here on the page changes.
        boolean rootChanged = target != null && !target.getAccessRootId().equals(page.getRootId());
        if (target != null) {
            kalkanPageMapperHelper.setParent(page, target);
        }
        page.setPosition(request.position());
        writePage(page, List.of(), user.getId());
        int descendants = rootChanged ? kalkanPageRepository.updateDescendantRoot(page.getId(), page.getRootId()) : 0;
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PAGE_MOVED,
                page.getId(), request.parentId(), request.position(), organizationId, user.getId(), descendants,
                page.getRootId());
        return kalkanPageQueryService.toDetailResponse(page, level);
    }

    @Override
    @Transactional
    public void deletePage(User user, UUID organizationId, UUID pageId) {
        KalkanPage page = kalkanPageQueryService.getEntity(organizationId, pageId);
        kalkanAccessQueryService.verifyLevel(organizationId, page, user, KalkanLevel.minimumToDelete(page.getParent() == null));
        // One instant for every row, so a restore brings back exactly what this delete took.
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        // Translations first: their query walks the subtree while its pages are still live.
        int translations = kalkanPageTranslationRepository.softDeleteBySubtree(page.getId(), user.getId(), now);
        int pages = kalkanPageRepository.softDeleteSubtree(page.getId(), user.getId(), now);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PAGE_DELETED,
                pageId, organizationId, user.getId(), pages, translations);
    }

    @Override
    @Transactional
    public KalkanPageDetailResponse restorePage(User user, UUID organizationId, UUID pageId) {
        KalkanPageStateProjection state = kalkanPageRepository.findStateIncludingDeleted(pageId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                            KalkanPageServiceLogConstant.DELETED_PAGE_NOT_FOUND, pageId, organizationId);
                    return new NotFoundException(ExceptionDomainKalkanError.NOT_PAGE_FOUND,
                            Map.of(KalkanPageConstant.FIELD_PAGE_ID, pageId.toString()));
                });
        boolean root = state.getParentId() == null;
        UUID accessRootId = root ? state.getId() : state.getRootId();
        // The level comes first, so a page the caller cannot see answers 404 whatever its state.
        KalkanLevel level = kalkanAccessQueryService.verifyLevel(organizationId, pageId, accessRootId, user,
                KalkanLevel.minimumToDelete(root));
        if (!Boolean.TRUE.equals(state.getDeleted())) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PAGE_NOT_DELETED,
                    pageId, organizationId);
            throw new ValidationException(ExceptionDomainKalkanError.VAL_PAGE_NOT_DELETED,
                    Map.of(KalkanPageConstant.FIELD_PAGE_ID, pageId.toString()));
        }
        if (Boolean.TRUE.equals(state.getParentDeleted())) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PARENT_DELETED,
                    pageId, organizationId);
            throw new ConflictException(ExceptionDomainKalkanError.STA_PARENT_DELETED,
                    Map.of(KalkanPageConstant.FIELD_PAGE_ID, pageId.toString(),
                            KalkanPageConstant.FIELD_PARENT_ID, state.getParentId().toString()));
        }
        // A path is what pushes and links target, so a slug a live sibling took meanwhile is refused,
        // not replaced by a free one.
        boolean slugTaken = root
                ? kalkanPageRepository.existsRootSlug(organizationId, state.getSlug(), null)
                : kalkanPageRepository.existsChildSlug(state.getParentId(), state.getSlug(), null);
        if (slugTaken) {
            throw slugTaken(organizationId, state.getParentId(), state.getSlug());
        }
        // A move after the delete checks only the live subtree, so the pages brought back may no
        // longer fit under the depth limit.
        int depth = kalkanPageRepository.findRestoredDepth(pageId);
        if (depth > KalkanPageConstant.MAX_DEPTH) {
            throw tooDeepToRestore(organizationId, pageId, depth);
        }

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        // Translations first: their query matches the pages by the delete instant they still carry.
        int translations = kalkanPageTranslationRepository.restoreBySubtree(pageId, user.getId(), now);
        int pages = kalkanPageRepository.restoreSubtree(pageId, user.getId(), now);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PAGE_RESTORED,
                pageId, organizationId, user.getId(), pages, translations);
        return kalkanPageQueryService.toDetailResponse(kalkanPageQueryService.getEntity(organizationId, pageId),
                level);
    }

    @Override
    @Transactional
    public KalkanPageUpsertResponse upsertByPath(User user, UUID organizationId, KalkanPageUpsertRequest upsert,
                                                 boolean dryRun) {
        KalkanPageUpsertRequest request = new KalkanPageUpsertRequest(upsert.path(), upsert.position(),
                upsert.compliance(), unsigned(upsert.translations()), upsert.force());
        requireDistinctLocales(organizationId, request.translations());
        String path = request.path();
        String[] slugs = path.split(KalkanPageConstant.PATH_SEPARATOR);

        Optional<KalkanPage> root = kalkanPageRepository.findByOrganizationIdAndParentIsNullAndSlug(organizationId,
                slugs[0]);
        if (root.isEmpty()) {
            if (slugs.length > 1) {
                throw parentPathMissing(organizationId, path, slugs[0]);
            }
            kalkanAccessQueryService.requireRootCreate(organizationId, user);
            requireNotReservedRootSlug(organizationId, slugs[0]);
            requireRootSlugNotInTrash(organizationId, slugs[0]);
            return createByPath(user, organizationId, null, request, dryRun);
        }
        // The root's level is checked before any deeper page is looked up, so a hidden tree reveals nothing.
        kalkanAccessQueryService.verifyLevel(organizationId, root.get(), user, KalkanLevel.EDIT);

        KalkanPage current = root.get();
        for (int i = 1; i < slugs.length; i++) {
            Optional<KalkanPage> child = kalkanPageRepository.findByParent_IdAndSlug(current.getId(), slugs[i]);
            if (child.isEmpty()) {
                if (i < slugs.length - 1) {
                    throw parentPathMissing(organizationId, path, slugs[i]);
                }
                return createByPath(user, organizationId, current, request, dryRun);
            }
            current = child.get();
        }
        return updateByPath(user, organizationId, current, request, dryRun);
    }

    // ---- Upsert ----

    private KalkanPageUpsertResponse createByPath(User user, UUID organizationId, KalkanPage parent,
                                                  KalkanPageUpsertRequest request, boolean dryRun) {
        if (dryRun) {
            return kalkanPageMapper.toUpsertResponse(null, request.path(), KalkanUpsertOutcome.CREATED, 0L);
        }
        String slug = slugOf(request.path());
        int position = request.position() != null ? request.position() : nextPosition(organizationId, parent);
        boolean compliance = Boolean.TRUE.equals(request.compliance());
        KalkanPage page = insertPage(organizationId, parent, slug, position, compliance, request.translations(),
                user.getId());
        long version = page.getVersion();
        kalkanPageRepository.markPushed(page.getId());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PAGE_UPSERTED,
                request.path(), organizationId, user.getId(), KalkanUpsertOutcome.CREATED, false);
        return kalkanPageMapper.toUpsertResponse(page.getId(), request.path(), KalkanUpsertOutcome.CREATED, version);
    }

    private KalkanPageUpsertResponse updateByPath(User user, UUID organizationId, KalkanPage page,
                                                  KalkanPageUpsertRequest request, boolean dryRun) {
        Map<KalkanLocale, KalkanPageTranslation> stored = storedTranslations(page);
        boolean complianceChanged = request.compliance() != null && request.compliance() != page.isCompliance();
        boolean positionChanged = request.position() != null && request.position() != page.getPosition();
        boolean translationsChanged = request.translations().stream()
                .anyMatch(translation -> !sameText(stored.get(translation.locale()), translation));
        if (!complianceChanged && !positionChanged && !translationsChanged) {
            return kalkanPageMapper.toUpsertResponse(page.getId(), request.path(), KalkanUpsertOutcome.UNCHANGED,
                    page.getVersion());
        }
        // A push owns a page only while nobody changed it in Kalkan since the push wrote it.
        Long pushedVersion = page.getPushedVersion();
        if (!request.force() && (pushedVersion == null || pushedVersion != page.getVersion())) {
            throw changedSincePush(organizationId, request.path(), page);
        }
        if (dryRun) {
            return kalkanPageMapper.toUpsertResponse(page.getId(), request.path(), KalkanUpsertOutcome.UPDATED,
                    page.getVersion());
        }

        // Every read is done; from here on the page and its translations change.
        if (complianceChanged) {
            page.setCompliance(request.compliance());
        }
        if (positionChanged) {
            page.setPosition(request.position());
        }
        List<KalkanPageTranslation> changed = applyTranslations(page, stored, request.translations(), user.getId());
        writePage(page, changed, user.getId());
        long version = page.getVersion();
        kalkanPageRepository.markPushed(page.getId());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PAGE_UPSERTED,
                request.path(), organizationId, user.getId(), KalkanUpsertOutcome.UPDATED, false);
        return kalkanPageMapper.toUpsertResponse(page.getId(), request.path(), KalkanUpsertOutcome.UPDATED, version);
    }

    // ---- Writes ----

    /**
     * Inserts a page with its translations and their first revisions; a root also gets its
     * creator's MANAGE grant in the same transaction.
     */
    private KalkanPage insertPage(UUID organizationId, KalkanPage parent, String slug, int position,
                                  boolean compliance, List<KalkanPageTranslationRequest> requests, UUID userId) {
        KalkanPage page = kalkanPageRepository.saveAndFlush(
                kalkanPageMapperHelper.newPage(organizationId, parent, slug, position, compliance, userId));
        if (parent == null) {
            kalkanPermissionService.grantCreator(page, userId);
        }
        List<KalkanPageTranslation> translations = new ArrayList<>(requests.size());
        for (KalkanPageTranslationRequest request : requests) {
            KalkanPageTranslation translation = kalkanPageMapper.toTranslationEntity(request);
            kalkanPageMapperHelper.setCreateRelations(translation, page, userId);
            translations.add(translation);
        }
        kalkanPageTranslationRepository.saveAllAndFlush(translations);
        appendRevisions(translations, page.getVersion());
        return page;
    }

    /**
     * Writes the given locales onto the stored translations, creating the missing ones, and returns
     * the translations whose title or body changed. An unchanged locale is left alone, so it gets
     * no revision.
     */
    private List<KalkanPageTranslation> applyTranslations(KalkanPage page,
                                                          Map<KalkanLocale, KalkanPageTranslation> stored,
                                                          List<KalkanPageTranslationRequest> requests, UUID userId) {
        List<KalkanPageTranslation> changed = new ArrayList<>();
        for (KalkanPageTranslationRequest request : requests) {
            KalkanPageTranslation translation = stored.get(request.locale());
            if (translation == null) {
                translation = kalkanPageMapper.toTranslationEntity(request);
                kalkanPageMapperHelper.setCreateRelations(translation, page, userId);
                kalkanPageTranslationRepository.save(translation);
                changed.add(translation);
            } else if (!sameText(translation, request)) {
                kalkanPageMapper.updateTranslationEntity(translation, request);
                translation.setUpdatedById(userId);
                changed.add(translation);
            }
        }
        return changed;
    }

    /**
     * Writes the page row, which bumps its version, and appends a revision per changed translation
     * carrying that new version.
     */
    private void writePage(KalkanPage page, List<KalkanPageTranslation> changed, UUID userId) {
        page.setUpdatedById(userId);
        // Marks the row as changed even when only translations changed, so the version still moves.
        page.setUpdatedAt(Instant.now(clock));
        kalkanPageRepository.saveAndFlush(page);
        appendRevisions(changed, page.getVersion());
    }

    private void appendRevisions(List<KalkanPageTranslation> translations, long pageVersion) {
        if (translations.isEmpty()) {
            return;
        }
        kalkanPageRevisionRepository.saveAll(translations.stream()
                .map(translation -> kalkanPageMapper.toRevision(translation, pageVersion))
                .toList());
    }

    // ---- Reads and checks ----

    private Map<KalkanLocale, KalkanPageTranslation> storedTranslations(KalkanPage page) {
        Map<KalkanLocale, KalkanPageTranslation> stored = new EnumMap<>(KalkanLocale.class);
        for (KalkanPageTranslation translation : kalkanPageTranslationRepository
                .findByPage_IdOrderByLocaleAsc(page.getId())) {
            stored.put(translation.getLocale(), translation);
        }
        return stored;
    }

    /**
     * The translations with every blob URL in their bodies unsigned. Reads sign the URLs, so an
     * editor sends signed ones back; the stored body, its comparison and its revisions use the
     * unsigned form, which does not expire.
     */
    private List<KalkanPageTranslationRequest> unsigned(List<KalkanPageTranslationRequest> translations) {
        return translations.stream()
                .map(translation -> new KalkanPageTranslationRequest(translation.locale(), translation.title(),
                        blobUrlRewriter.stripMarkdownBlobUrlSignatures(translation.body())))
                .toList();
    }

    private static boolean sameText(KalkanPageTranslation stored, KalkanPageTranslationRequest request) {
        return stored != null && stored.getTitle().equals(request.title()) && stored.getBody().equals(request.body());
    }

    private int nextPosition(UUID organizationId, KalkanPage parent) {
        Optional<Integer> max = parent == null
                ? kalkanPageRepository.findMaxRootPosition(organizationId)
                : kalkanPageRepository.findMaxChildPosition(parent.getId());
        return max.map(position -> position + 1).orElse(0);
    }

    private static String slugOf(String path) {
        return path.substring(path.lastIndexOf(KalkanPageConstant.PATH_SEPARATOR) + 1);
    }

    private void requireVersion(UUID organizationId, KalkanPage page, Long version) {
        if (version != page.getVersion()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.VERSION_OUTDATED,
                    page.getId(), organizationId, version, page.getVersion());
            Map<String, Object> errorValues = new HashMap<>();
            errorValues.put(KalkanPageConstant.FIELD_PAGE_ID, page.getId().toString());
            errorValues.put(KalkanPageConstant.FIELD_VERSION, page.getVersion());
            throw new ConflictException(ExceptionDomainKalkanError.STA_VERSION_OUTDATED, errorValues);
        }
    }

    private void requireDistinctLocales(UUID organizationId, List<KalkanPageTranslationRequest> translations) {
        Set<KalkanLocale> seen = EnumSet.noneOf(KalkanLocale.class);
        for (KalkanPageTranslationRequest translation : translations) {
            if (!seen.add(translation.locale())) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.DUPLICATE_LOCALE,
                        organizationId, translation.locale());
                throw new ValidationException(ExceptionDomainKalkanError.VAL_DUPLICATE_LOCALE,
                        Map.of(KalkanPageConstant.FIELD_LOCALE, translation.locale().name()));
            }
        }
    }

    private void requireNotReservedRootSlug(UUID organizationId, String slug) {
        if (KalkanPageConstant.RESERVED_ROOT_SLUGS.contains(slug.toLowerCase(Locale.ROOT))) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.RESERVED_ROOT_SLUG,
                    slug, organizationId);
            throw new ValidationException(ExceptionDomainKalkanError.VAL_RESERVED_ROOT_SLUG,
                    Map.of(KalkanPageConstant.FIELD_SLUG, slug));
        }
    }

    /**
     * A root's slug is the fixed route segment of its section: the web app routes the section by it
     * and pushes name it as the first segment of their paths, so a root keeps the slug it was
     * created with, whoever asks for the change.
     */
    private void requireSlugChangeBelowRoot(UUID organizationId, KalkanPage page, String slug) {
        if (page.getParent() == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.ROOT_SLUG_FIXED,
                    page.getId(), organizationId, page.getSlug(), slug);
            throw new ValidationException(ExceptionDomainKalkanError.VAL_ROOT_SLUG_FIXED,
                    Map.of(KalkanPageConstant.FIELD_PAGE_ID, page.getId().toString(),
                            KalkanPageConstant.FIELD_SLUG, page.getSlug()));
        }
    }

    private void requireSlugFree(UUID organizationId, KalkanPage parent, String slug, UUID excludedId) {
        boolean taken = parent == null
                ? kalkanPageRepository.existsRootSlug(organizationId, slug, excludedId)
                : kalkanPageRepository.existsChildSlug(parent.getId(), slug, excludedId);
        if (taken) {
            throw slugTaken(organizationId, parent == null ? null : parent.getId(), slug);
        }
    }

    /**
     * A deleted root keeps its slug: a second root with it would leave the section split in two and
     * make the old root's restore fail, so the deleted root has to be restored instead.
     */
    private void requireRootSlugNotInTrash(UUID organizationId, String slug) {
        Optional<UUID> deletedRootId = kalkanPageRepository.findDeletedRootIdBySlug(organizationId, slug);
        if (deletedRootId.isPresent()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.ROOT_SLUG_IN_TRASH,
                    slug, organizationId, deletedRootId.get());
            throw new ConflictException(ExceptionDomainKalkanError.STA_SLUG_TAKEN,
                    Map.of(KalkanPageConstant.FIELD_SLUG, slug,
                            KalkanPageConstant.FIELD_DELETED_PAGE_ID, deletedRootId.get().toString()));
        }
    }

    private void requireDepthForChild(UUID organizationId, KalkanPage parent, String slug) {
        if (kalkanPageMapperHelper.depthOf(parent) + 1 > KalkanPageConstant.MAX_DEPTH) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.DEPTH_EXCEEDED,
                    slug, parent.getId(), organizationId, KalkanPageConstant.MAX_DEPTH);
            Map<String, Object> errorValues = new HashMap<>();
            errorValues.put(KalkanPageConstant.FIELD_PARENT_ID, parent.getId().toString());
            errorValues.put(KalkanPageConstant.FIELD_REASON, KalkanPageConstant.REASON_TOO_DEEP);
            errorValues.put(KalkanPageConstant.FIELD_MAX_DEPTH, KalkanPageConstant.MAX_DEPTH);
            throw new ValidationException(ExceptionDomainKalkanError.VAL_MOVE_INVALID, errorValues);
        }
    }

    // ---- Errors ----

    private ConflictException slugTaken(UUID organizationId, UUID parentId, String slug) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.SLUG_TAKEN,
                slug, parentId, organizationId);
        return new ConflictException(ExceptionDomainKalkanError.STA_SLUG_TAKEN,
                Map.of(KalkanPageConstant.FIELD_SLUG, slug));
    }

    private ValidationException invalidMove(UUID organizationId, KalkanPage page, UUID parentId, String reason) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.MOVE_INVALID,
                page.getId(), parentId, organizationId, reason);
        Map<String, Object> errorValues = new HashMap<>();
        errorValues.put(KalkanPageConstant.FIELD_PAGE_ID, page.getId().toString());
        errorValues.put(KalkanPageConstant.FIELD_REASON, reason);
        if (KalkanPageConstant.REASON_TOO_DEEP.equals(reason)) {
            errorValues.put(KalkanPageConstant.FIELD_MAX_DEPTH, KalkanPageConstant.MAX_DEPTH);
        }
        return new ValidationException(ExceptionDomainKalkanError.VAL_MOVE_INVALID, errorValues);
    }

    private ValidationException tooDeepToRestore(UUID organizationId, UUID pageId, int depth) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.RESTORE_TOO_DEEP,
                pageId, organizationId, depth, KalkanPageConstant.MAX_DEPTH);
        Map<String, Object> errorValues = new HashMap<>();
        errorValues.put(KalkanPageConstant.FIELD_PAGE_ID, pageId.toString());
        errorValues.put(KalkanPageConstant.FIELD_REASON, KalkanPageConstant.REASON_TOO_DEEP);
        errorValues.put(KalkanPageConstant.FIELD_MAX_DEPTH, KalkanPageConstant.MAX_DEPTH);
        return new ValidationException(ExceptionDomainKalkanError.VAL_MOVE_INVALID, errorValues);
    }

    private ValidationException parentPathMissing(UUID organizationId, String path, String slug) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PARENT_PATH_MISSING,
                path, organizationId, slug);
        return new ValidationException(ExceptionDomainKalkanError.VAL_PARENT_PATH_MISSING,
                Map.of(KalkanPageConstant.FIELD_PATH, path, KalkanPageConstant.FIELD_SLUG, slug));
    }

    private ConflictException changedSincePush(UUID organizationId, String path, KalkanPage page) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.CHANGED_SINCE_PUSH,
                path, organizationId, page.getVersion(), page.getPushedVersion());
        // The pusher needs who changed the page and when, to settle the edit before forcing.
        Map<String, Object> errorValues = new HashMap<>();
        errorValues.put(KalkanPageConstant.FIELD_PATH, path);
        errorValues.put(KalkanPageConstant.FIELD_UPDATED_BY_ID, page.getUpdatedById().toString());
        String name = kalkanPageMapperHelper.displayNames(List.of(page.getUpdatedById())).get(page.getUpdatedById());
        if (name != null) {
            errorValues.put(KalkanPageConstant.FIELD_UPDATED_BY_NAME, name);
        }
        errorValues.put(KalkanPageConstant.FIELD_UPDATED_AT, page.getUpdatedAt().toString());
        return new ConflictException(ExceptionDomainKalkanError.STA_CHANGED_SINCE_PUSH, errorValues);
    }
}
