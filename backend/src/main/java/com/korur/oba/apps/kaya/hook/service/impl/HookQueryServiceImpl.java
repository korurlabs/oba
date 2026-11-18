package com.korur.oba.apps.kaya.hook.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.hook.constant.HookConstant;
import com.korur.oba.apps.kaya.hook.constant.service.HookServiceLogConstant;
import com.korur.oba.apps.kaya.hook.dto.request.HookQueryRequest;
import com.korur.oba.apps.kaya.hook.dto.response.HookDetailResponse;
import com.korur.oba.apps.kaya.hook.dto.response.HookListResponse;
import com.korur.oba.apps.kaya.hook.entity.Hook;
import com.korur.oba.apps.kaya.hook.mapper.HookMapper;
import com.korur.oba.apps.kaya.hook.repository.HookRepository;
import com.korur.oba.apps.kaya.hook.service.HookQueryService;
import com.korur.oba.apps.kaya.hook.specification.HookSpecification;

/**
 * The {@link HookQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HookQueryServiceImpl implements HookQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(HookQueryServiceImpl.class);

    // Version first, so the hooks of one version stay together when a page lists several.
    private static final Sort LIST_SORT = Sort.by(HookConstant.FIELD_VERSION_ID,
            HookConstant.FIELD_RELATIVE_PATH, HookConstant.FIELD_ID);

    private final HookRepository hookRepository;
    private final BlobQueryService blobQueryService;
    private final HookMapper hookMapper;
    private final LoggingService loggingService;

    @Override
    public List<HookListResponse> getHooks(UUID organizationId, HookQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, HookServiceLogConstant.LISTING_HOOKS,
                organizationId, request);
        List<Hook> hooks = hookRepository.findAll(HookSpecification.filterBy(organizationId, request), LIST_SORT);
        if (hooks.isEmpty()) {
            return List.of();
        }

        // The sizes are read in one query, without loading any content.
        Map<UUID, Integer> sizes = blobQueryService.getSizesByIds(organizationId,
                hooks.stream().map(Hook::getContentBlobId).collect(Collectors.toSet()));
        return hooks.stream()
                .map(hook -> hookMapper.toListResponse(hook, sizes.get(hook.getContentBlobId())))
                .toList();
    }

    @Override
    public HookDetailResponse getHook(UUID organizationId, UUID hookId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, HookServiceLogConstant.READING_HOOK, hookId, organizationId);
        Hook hook = getEntityById(organizationId, hookId);
        Blob blob = blobQueryService.getEntityById(organizationId, hook.getContentBlobId());
        return hookMapper.toDetailResponse(hook, blob.getSize(), new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    @Override
    public Hook getEntityById(UUID organizationId, UUID hookId) {
        // Looked up within the organization, so a hook of another organization answers the same 404 as
        // an unknown id.
        return hookRepository.findByIdAndOrganizationId(hookId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, HookServiceLogConstant.HOOK_NOT_FOUND, hookId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(HookConstant.FIELD_HOOK_ID, hookId.toString()));
        });
    }

    @Override
    public List<Hook> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, HookServiceLogConstant.LOADING_VERSION_HOOKS, versionId);
        return hookRepository.findByOrganizationIdAndVersionIdOrderByRelativePathAsc(organizationId, versionId);
    }
}
