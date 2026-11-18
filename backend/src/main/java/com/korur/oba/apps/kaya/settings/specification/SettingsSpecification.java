package com.korur.oba.apps.kaya.settings.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.settings.constant.SettingsConstant;
import com.korur.oba.apps.kaya.settings.dto.request.SettingsQueryRequest;
import com.korur.oba.apps.kaya.settings.entity.Settings;

/**
 * Filters of the settings list. Every list is limited to one organization, so a version id of another
 * organization matches nothing.
 */
public final class SettingsSpecification {

    private SettingsSpecification() {
    }

    private static Specification<Settings> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Settings> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(SettingsConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Settings> byVersionIds(List<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get(SettingsConstant.FIELD_VERSION_ID).in(versionIds);
    }

    /**
     * The settings of the organization among the requested versions.
     *
     * @param organizationId the organization of the request
     * @param request        the version ids
     * @return the combined filter
     */
    public static Specification<Settings> filterBy(UUID organizationId, SettingsQueryRequest request) {
        return byOrganization(organizationId)
                .and(byVersionIds(request.getVersionId()));
    }
}
