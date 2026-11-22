package com.korur.oba.apps.kalkan.page.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.core.exception.enums.ExceptionDomainKalkanError;
import com.korur.oba.apps.kalkan.page.constant.KalkanPageConstant;
import com.korur.oba.apps.kalkan.page.constant.service.KalkanPageServiceLogConstant;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageRecentProjection;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageRevisionListProjection;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageTrashProjection;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageTreeProjection;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageDetailResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageListResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageRecentResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageRevisionDetailResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageRevisionListResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageTrashResponse;
import com.korur.oba.apps.kalkan.page.dto.response.embedded.KalkanPageTranslationEmbeddedResponse;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.page.entity.KalkanPageRevision;
import com.korur.oba.apps.kalkan.page.entity.KalkanPageTranslation;
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;
import com.korur.oba.apps.kalkan.page.mapper.KalkanPageMapper;
import com.korur.oba.apps.kalkan.page.mapper.KalkanPageMapperHelper;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageRepository;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageRevisionRepository;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageTranslationRepository;
import com.korur.oba.apps.kalkan.page.service.KalkanPageQueryService;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.apps.kalkan.permission.service.KalkanAccessQueryService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * The {@link KalkanPageQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KalkanPageQueryServiceImpl implements KalkanPageQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(KalkanPageQueryServiceImpl.class);

    private final KalkanPageRepository kalkanPageRepository;
    private final KalkanPageTranslationRepository kalkanPageTranslationRepository;
    private final KalkanPageRevisionRepository kalkanPageRevisionRepository;
    private final KalkanAccessQueryService kalkanAccessQueryService;
    private final KalkanPageMapper kalkanPageMapper;
    private final KalkanPageMapperHelper kalkanPageMapperHelper;
    private final LoggingService loggingService;

    @Override
    public KalkanPage getEntity(UUID organizationId, UUID pageId) {
        return kalkanPageRepository.findByIdAndOrganizationId(pageId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PAGE_NOT_FOUND,
                            pageId, organizationId);
                    return new NotFoundException(ExceptionDomainKalkanError.NOT_PAGE_FOUND,
                            Map.of(KalkanPageConstant.FIELD_PAGE_ID, pageId.toString()));
                });
    }

    @Override
    public KalkanPage getEntityById(UUID pageId) {
        return kalkanPageRepository.findById(pageId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.PAGE_NOT_FOUND_BY_ID,
                            pageId);
                    return new NotFoundException(ExceptionDomainKalkanError.NOT_PAGE_FOUND,
                            Map.of(KalkanPageConstant.FIELD_PAGE_ID, pageId.toString()));
                });
    }

    @Override
    public List<KalkanPageListResponse> getTree(User user, UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.LISTING_TREE,
                organizationId, user.getId());
        List<UUID> rootIds = kalkanPageRepository.findByOrganizationIdAndParentIsNull(organizationId).stream()
                .map(KalkanPage::getId)
                .toList();
        if (rootIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, KalkanLevel> levels = kalkanAccessQueryService.resolveLevels(organizationId, rootIds, user);
        if (levels.isEmpty()) {
            return List.of();
        }
        List<KalkanPageTreeProjection> rows = kalkanPageRepository.findTree(organizationId, levels.keySet());
        Map<UUID, Map<KalkanLocale, String>> titles = kalkanPageMapperHelper.titlesByPage(
                kalkanPageTranslationRepository.findTitles(rows.stream().map(KalkanPageTreeProjection::getId).toList()));
        List<KalkanPageListResponse> responses = new ArrayList<>(rows.size());
        for (KalkanPageTreeProjection row : rows) {
            // Only a root carries the level: every page below inherits it.
            KalkanLevel level = row.getParentId() == null ? levels.get(row.getId()) : null;
            responses.add(kalkanPageMapper.toListResponse(row,
                    titles.getOrDefault(row.getId(), new EnumMap<>(KalkanLocale.class)), level));
        }
        return responses;
    }

    @Override
    public KalkanPageDetailResponse getPage(User user, UUID organizationId, UUID pageId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.READING_PAGE,
                pageId, organizationId);
        KalkanPage page = getEntity(organizationId, pageId);
        KalkanLevel level = kalkanAccessQueryService.verifyLevel(organizationId, page, user, KalkanLevel.VIEW);
        return toDetailResponse(page, level);
    }

    @Override
    public List<KalkanPageRevisionListResponse> getRevisions(User user, UUID organizationId, UUID pageId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.LISTING_REVISIONS,
                pageId, organizationId);
        KalkanPage page = getEntity(organizationId, pageId);
        kalkanAccessQueryService.verifyLevel(organizationId, page, user, KalkanLevel.VIEW);
        List<KalkanPageRevisionListProjection> rows = kalkanPageRevisionRepository.findListByPageId(page.getId());
        Map<UUID, String> names = kalkanPageMapperHelper.displayNames(
                rows.stream().map(KalkanPageRevisionListProjection::getCreatedById).toList());
        return rows.stream()
                .map(row -> kalkanPageMapper.toRevisionListResponse(row, names.get(row.getCreatedById())))
                .toList();
    }

    @Override
    public KalkanPageRevisionDetailResponse getRevision(User user, UUID organizationId, UUID pageId,
                                                        UUID revisionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.READING_REVISION,
                revisionId, pageId, organizationId);
        KalkanPage page = getEntity(organizationId, pageId);
        // The level comes first, so a revision of a hidden page answers the page's 404.
        kalkanAccessQueryService.verifyLevel(organizationId, page, user, KalkanLevel.VIEW);
        KalkanPageRevision revision = kalkanPageRevisionRepository.findByIdAndPageId(revisionId, page.getId())
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.REVISION_NOT_FOUND,
                            revisionId, pageId, organizationId);
                    return new NotFoundException(ExceptionDomainKalkanError.NOT_REVISION_FOUND,
                            Map.of(KalkanPageConstant.FIELD_PAGE_ID, pageId.toString(),
                                    KalkanPageConstant.FIELD_REVISION_ID, revisionId.toString()));
                });
        String name = kalkanPageMapperHelper.displayNames(List.of(revision.getCreatedById()))
                .get(revision.getCreatedById());
        return kalkanPageMapper.toRevisionDetailResponse(revision, name);
    }

    @Override
    public List<KalkanPageTrashResponse> getTrash(User user, UUID organizationId, KalkanLocale locale) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.LISTING_TRASH,
                organizationId, user.getId());
        List<UUID> rootIds = kalkanPageRepository.findRootIdsIncludingDeleted(organizationId);
        if (rootIds.isEmpty()) {
            return List.of();
        }
        // The trash lists exactly what the caller may restore, by the level restorePage checks: a
        // non-root page needs the lower level, so only roots reaching it are searched and a deleted
        // root is then kept only for its managers.
        Map<UUID, KalkanLevel> levels = kalkanAccessQueryService.resolveLevels(organizationId, rootIds, user);
        List<UUID> searchedRootIds = levels.entrySet().stream()
                .filter(entry -> entry.getValue().isAtLeast(KalkanLevel.minimumToDelete(false)))
                .map(Map.Entry::getKey)
                .toList();
        if (searchedRootIds.isEmpty()) {
            return List.of();
        }
        List<KalkanPageTrashProjection> rows = kalkanPageRepository.findTrash(organizationId, searchedRootIds)
                .stream()
                .filter(row -> levels.get(row.getAccessRootId())
                        .isAtLeast(KalkanLevel.minimumToDelete(Boolean.TRUE.equals(row.getRoot()))))
                .toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Map<KalkanLocale, String>> titles = kalkanPageMapperHelper.titlesByPage(
                kalkanPageTranslationRepository.findDeletedTitles(
                        rows.stream().map(KalkanPageTrashProjection::getId).toList()));
        Map<UUID, String> names = kalkanPageMapperHelper.displayNames(
                rows.stream().map(KalkanPageTrashProjection::getDeletedById).toList());
        List<KalkanPageTrashResponse> responses = new ArrayList<>(rows.size());
        for (KalkanPageTrashProjection row : rows) {
            responses.add(kalkanPageMapper.toTrashResponse(row,
                    kalkanPageMapperHelper.titleIn(titles.get(row.getId()), locale),
                    names.get(row.getDeletedById())));
        }
        return responses;
    }

    @Override
    public List<KalkanPageRecentResponse> getRecent(User user, UUID organizationId, int limit, KalkanLocale locale) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, KalkanPageServiceLogConstant.LISTING_RECENT,
                limit, organizationId, user.getId());
        List<UUID> rootIds = kalkanPageRepository.findByOrganizationIdAndParentIsNull(organizationId).stream()
                .map(KalkanPage::getId)
                .toList();
        if (rootIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, KalkanLevel> levels = kalkanAccessQueryService.resolveLevels(organizationId, rootIds, user);
        if (levels.isEmpty()) {
            return List.of();
        }
        List<KalkanPageRecentProjection> rows = kalkanPageRepository.findRecent(organizationId, levels.keySet(),
                limit);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Map<KalkanLocale, String>> titles = kalkanPageMapperHelper.titlesByPage(
                kalkanPageTranslationRepository.findTitles(
                        rows.stream().map(KalkanPageRecentProjection::getId).toList()));
        Map<UUID, String> names = kalkanPageMapperHelper.displayNames(
                rows.stream().map(KalkanPageRecentProjection::getUpdatedById).toList());
        List<KalkanPageRecentResponse> responses = new ArrayList<>(rows.size());
        for (KalkanPageRecentProjection row : rows) {
            responses.add(kalkanPageMapper.toRecentResponse(row,
                    kalkanPageMapperHelper.titleIn(titles.get(row.getId()), locale),
                    names.get(row.getUpdatedById())));
        }
        return responses;
    }

    @Override
    public KalkanPageDetailResponse toDetailResponse(KalkanPage page, KalkanLevel level) {
        List<KalkanPageTranslation> translations = kalkanPageTranslationRepository
                .findByPage_IdOrderByLocaleAsc(page.getId());
        Map<UUID, String> names = kalkanPageMapperHelper.displayNames(
                translations.stream().map(KalkanPageTranslation::getUpdatedById).toList());
        List<KalkanPageTranslationEmbeddedResponse> embedded = translations.stream()
                .map(translation -> kalkanPageMapper.toTranslationEmbeddedResponse(translation,
                        names.get(translation.getUpdatedById())))
                .toList();
        return kalkanPageMapper.toDetailResponse(page, kalkanPageMapperHelper.pathOf(page), level, embedded);
    }
}
