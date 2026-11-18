package com.korur.oba.apps.kaya.settings.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.settings.constant.SettingsConstant;
import com.korur.oba.apps.kaya.settings.constant.service.SettingsServiceLogConstant;
import com.korur.oba.apps.kaya.settings.dto.request.SettingsQueryRequest;
import com.korur.oba.apps.kaya.settings.dto.response.SettingsResponse;
import com.korur.oba.apps.kaya.settings.entity.Settings;
import com.korur.oba.apps.kaya.settings.helper.SettingsDocument;
import com.korur.oba.apps.kaya.settings.helper.SettingsParser;
import com.korur.oba.apps.kaya.settings.mapper.SettingsMapper;
import com.korur.oba.apps.kaya.settings.repository.SettingsRepository;
import com.korur.oba.apps.kaya.settings.service.SettingsQueryService;
import com.korur.oba.apps.kaya.settings.specification.SettingsSpecification;

/**
 * The {@link SettingsQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsQueryServiceImpl implements SettingsQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SettingsQueryServiceImpl.class);

    // A version has at most one settings file, so version and id give a stable order.
    private static final Sort LIST_SORT = Sort.by(SettingsConstant.FIELD_VERSION_ID, SettingsConstant.FIELD_ID);

    private final SettingsRepository settingsRepository;
    private final BlobQueryService blobQueryService;
    private final SettingsParser settingsParser;
    private final SettingsMapper settingsMapper;
    private final LoggingService loggingService;

    @Override
    public List<SettingsResponse> getSettingsList(UUID organizationId, SettingsQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SettingsServiceLogConstant.LISTING_SETTINGS,
                organizationId, request);
        // At most one file per requested version, so at most 20 blob reads; the rules must be parsed
        // from the content, which the size query does not load.
        return settingsRepository.findAll(SettingsSpecification.filterBy(organizationId, request), LIST_SORT).stream()
                .map(settings -> toResponse(organizationId, settings))
                .toList();
    }

    @Override
    public SettingsResponse getSettings(UUID organizationId, UUID settingsId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SettingsServiceLogConstant.READING_SETTINGS,
                settingsId, organizationId);
        return toResponse(organizationId, getEntityById(organizationId, settingsId));
    }

    @Override
    public Settings getEntityById(UUID organizationId, UUID settingsId) {
        // Looked up within the organization, so settings of another organization answer the same 404 as
        // an unknown id.
        return settingsRepository.findByIdAndOrganizationId(settingsId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SettingsServiceLogConstant.SETTINGS_NOT_FOUND,
                    settingsId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(SettingsConstant.FIELD_SETTINGS_ID, settingsId.toString()));
        });
    }

    @Override
    public Optional<Settings> getEntityByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SettingsServiceLogConstant.LOADING_VERSION_SETTINGS, versionId);
        return settingsRepository.findByOrganizationIdAndVersionId(organizationId, versionId);
    }

    private SettingsResponse toResponse(UUID organizationId, Settings settings) {
        Blob blob = blobQueryService.getEntityById(organizationId, settings.getContentBlobId());
        Optional<SettingsDocument> parsed = settingsParser.parse(blob.getContent());
        if (parsed.isEmpty()) {
            // Parse rejects a settings file that is not a JSON object, so one here means the stored
            // file predates that check; the page shows no rules rather than failing.
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SettingsServiceLogConstant.SETTINGS_UNREADABLE,
                    settings.getId(), settings.getVersionId());
        }
        return settingsMapper.toResponse(settings, blob.getSize(), parsed.orElse(SettingsDocument.EMPTY));
    }
}
