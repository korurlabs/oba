package com.korur.oba.apps.kaya.settings.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.factory.Mappers;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.settings.dto.embedded.SettingsIndex;
import com.korur.oba.apps.kaya.settings.dto.request.SettingsQueryRequest;
import com.korur.oba.apps.kaya.settings.dto.response.SettingsResponse;
import com.korur.oba.apps.kaya.settings.entity.Settings;
import com.korur.oba.apps.kaya.settings.helper.SettingsParser;
import com.korur.oba.apps.kaya.settings.mapper.SettingsMapper;
import com.korur.oba.apps.kaya.settings.repository.SettingsRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServicesUnitTest {

    private static final String SETTINGS_JSON = """
            {
              "permissions": {"allow": ["Bash(git status)"], "ask": ["Bash(git push:*)"], "deny": ["Read(./.env)"]},
              "hooks": {"PreToolUse": [{"matcher": "Bash", "hooks": [{"type": "command", "command": ".agents/hooks/dispatch.sh"}]}]},
              "model": "opus"
            }
            """;

    @Mock
    private SettingsRepository settingsRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private SettingsQueryServiceImpl settingsQueryService;
    private SettingsServiceImpl settingsService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        settingsQueryService = new SettingsQueryServiceImpl(settingsRepository, blobQueryService,
                new SettingsParser(new ObjectMapper()), Mappers.getMapper(SettingsMapper.class), loggingService);
        settingsService = new SettingsServiceImpl(settingsRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    private Settings settings() {
        return Settings.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .build();
    }

    private Blob blob(Settings settings, String json) {
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        return Blob.builder().id(settings.getContentBlobId()).content(content).size(content.length).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSettingsList_validFile_returnsRulesAndHooksParsedFromBlob() {
        Settings settings = settings();
        SettingsQueryRequest request = new SettingsQueryRequest();
        request.setVersionId(List.of(versionId));
        when(settingsRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(settings));
        when(blobQueryService.getEntityById(organizationId, settings.getContentBlobId()))
                .thenReturn(blob(settings, SETTINGS_JSON));

        List<SettingsResponse> result = settingsQueryService.getSettingsList(organizationId, request);

        assertEquals(1, result.size());
        SettingsResponse response = result.get(0);
        assertEquals(versionId, response.versionId());
        assertEquals(List.of("Bash(git status)"), response.allow());
        assertEquals(List.of("Bash(git push:*)"), response.ask());
        assertEquals(List.of("Read(./.env)"), response.deny());
        assertEquals(1, response.hooks().size());
        assertEquals("PreToolUse", response.hooks().get(0).event());
        assertEquals("Bash", response.hooks().get(0).matcher());
        assertEquals(".agents/hooks/dispatch.sh", response.hooks().get(0).command());
        assertEquals(SETTINGS_JSON.getBytes(StandardCharsets.UTF_8).length, response.sizeBytes());
    }

    @Test
    void getSettings_fileNotAJsonObject_returnsNoRulesOrHooks() {
        Settings settings = settings();
        when(settingsRepository.findByIdAndOrganizationId(settings.getId(), organizationId))
                .thenReturn(Optional.of(settings));
        when(blobQueryService.getEntityById(organizationId, settings.getContentBlobId()))
                .thenReturn(blob(settings, "[1, 2"));

        SettingsResponse result = settingsQueryService.getSettings(organizationId, settings.getId());

        assertTrue(result.allow().isEmpty());
        assertTrue(result.ask().isEmpty());
        assertTrue(result.deny().isEmpty());
        assertTrue(result.hooks().isEmpty());
    }

    @Test
    void getSettings_unknownOrOtherOrganization_throwsNotFound() {
        UUID settingsId = UUID.randomUUID();
        when(settingsRepository.findByIdAndOrganizationId(settingsId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> settingsQueryService.getSettings(organizationId, settingsId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    void replaceVersion_index_deletesFirstAndWritesSettings() {
        UUID blobId = UUID.randomUUID();
        when(settingsRepository.save(any(Settings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Settings> result = settingsService.replaceVersion(organizationId, versionId, new SettingsIndex(blobId));

        InOrder order = inOrder(settingsRepository);
        order.verify(settingsRepository).deleteByVersion(organizationId, versionId);
        order.verify(settingsRepository).save(any(Settings.class));
        assertTrue(result.isPresent());
        assertEquals(organizationId, result.get().getOrganizationId());
        assertEquals(versionId, result.get().getVersionId());
        assertEquals(blobId, result.get().getContentBlobId());
    }

    @Test
    void replaceVersion_noSettingsFile_deletesOnly() {
        Optional<Settings> result = settingsService.replaceVersion(organizationId, versionId, null);

        verify(settingsRepository).deleteByVersion(organizationId, versionId);
        verify(settingsRepository, never()).save(any(Settings.class));
        assertTrue(result.isEmpty());
    }
}
