package com.korur.oba.apps.kalkan.page.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.core.exception.enums.ExceptionDomainKalkanError;
import com.korur.oba.apps.kalkan.page.constant.KalkanPageConstant;
import com.korur.oba.apps.kalkan.page.constant.service.KalkanPageServiceLogConstant;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageTitleProjection;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageTreeProjection;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageDetailResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageListResponse;
import com.korur.oba.apps.kalkan.page.dto.response.embedded.KalkanPageTranslationEmbeddedResponse;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.page.entity.KalkanPageTranslation;
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;
import com.korur.oba.apps.kalkan.page.mapper.KalkanPageMapper;
import com.korur.oba.apps.kalkan.page.mapper.KalkanPageMapperHelper;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageRepository;
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
        Map<UUID, Map<KalkanLocale, String>> titles = new HashMap<>();
        for (KalkanPageTitleProjection title : kalkanPageTranslationRepository
                .findTitles(rows.stream().map(KalkanPageTreeProjection::getId).toList())) {
            titles.computeIfAbsent(title.getPageId(), id -> new EnumMap<>(KalkanLocale.class))
                    .put(title.getLocale(), title.getTitle());
        }
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
