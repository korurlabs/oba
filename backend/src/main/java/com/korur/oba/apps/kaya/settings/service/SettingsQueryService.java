package com.korur.oba.apps.kaya.settings.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.settings.dto.request.SettingsQueryRequest;
import com.korur.oba.apps.kaya.settings.dto.response.SettingsResponse;
import com.korur.oba.apps.kaya.settings.entity.Settings;

/**
 * Reads the {@link Settings} of an organization's versions and parses their permission rules and hooks
 * with {@link com.korur.oba.apps.kaya.settings.helper.SettingsParser} from the blobs read through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.settings.controller.SettingsController} for the Allowlist page,
 * and by the render and release domains to load the settings of a version.
 */
public interface SettingsQueryService {

    /**
     * Lists the settings of the given versions, ordered by version and id, with the rules and hooks
     * parsed from each file. A version id of another organization matches nothing; a file that is not a
     * JSON object is listed with no rules or hooks.
     *
     * @param organizationId the organization of the request
     * @param request        the 1 to 20 version ids
     * @return one entry per version that has a settings file
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the blob
     *         of a listed settings file is missing
     */
    List<SettingsResponse> getSettingsList(UUID organizationId, SettingsQueryRequest request);

    /**
     * Reads one settings file with the rules and hooks parsed from it.
     *
     * @param organizationId the organization of the request
     * @param settingsId     the settings
     * @return the settings with their rules and hooks
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such settings, or its blob is missing
     */
    SettingsResponse getSettings(UUID organizationId, UUID settingsId);

    /**
     * Loads settings of the organization.
     *
     * Settings of another organization answer the same 404 as an unknown id, so their existence is not
     * revealed.
     *
     * @param organizationId the organization of the request
     * @param settingsId     the settings
     * @return the settings
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such settings
     */
    Settings getEntityById(UUID organizationId, UUID settingsId);

    /**
     * Loads the settings of one version of the organization.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the settings; empty when the version has none or belongs to another organization
     */
    Optional<Settings> getEntityByVersionId(UUID organizationId, UUID versionId);
}
