package com.korur.oba.apps.kaya.settings.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.settings.dto.response.SettingsResponse;
import com.korur.oba.apps.kaya.settings.dto.response.embedded.SettingsHookEmbeddedResponse;
import com.korur.oba.apps.kaya.settings.entity.Settings;
import com.korur.oba.apps.kaya.settings.helper.SettingsDocument;
import com.korur.oba.apps.kaya.settings.helper.SettingsHook;

/**
 * Maps {@link Settings} with their size and parsed {@link SettingsDocument} to {@link SettingsResponse}s.
 */
@Mapper(componentModel = "spring")
public interface SettingsMapper {

    @Mapping(target = "id", source = "settings.id")
    @Mapping(target = "versionId", source = "settings.versionId")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "allow", source = "document.allow")
    @Mapping(target = "ask", source = "document.ask")
    @Mapping(target = "deny", source = "document.deny")
    @Mapping(target = "hooks", source = "document.hooks")
    SettingsResponse toResponse(Settings settings, Integer sizeBytes, SettingsDocument document);

    SettingsHookEmbeddedResponse toHookResponse(SettingsHook hook);
}
