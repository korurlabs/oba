package com.korur.oba.apps.kaya.settings.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.settings.constant.service.SettingsServiceLogConstant;
import com.korur.oba.apps.kaya.settings.dto.embedded.SettingsIndex;
import com.korur.oba.apps.kaya.settings.entity.Settings;
import com.korur.oba.apps.kaya.settings.repository.SettingsRepository;
import com.korur.oba.apps.kaya.settings.service.SettingsService;

/**
 * The {@link SettingsService} implementation.
 */
@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SettingsServiceImpl.class);

    private final SettingsRepository settingsRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public Optional<Settings> replaceVersion(UUID organizationId, UUID versionId, SettingsIndex settings) {
        // The old row is deleted and flushed first, so the new row does not collide with it on the
        // one-settings-per-version unique index.
        int removed = settingsRepository.deleteByVersion(organizationId, versionId);
        Optional<Settings> saved = Optional.ofNullable(settings)
                .map(index -> settingsRepository.save(toEntity(organizationId, versionId, index)));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SettingsServiceLogConstant.SETTINGS_REPLACED,
                versionId, organizationId, removed, saved.isPresent() ? 1 : 0);
        return saved;
    }

    private Settings toEntity(UUID organizationId, UUID versionId, SettingsIndex index) {
        return Settings.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .build();
    }
}
