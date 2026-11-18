package com.korur.oba.apps.kaya.guardrail.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.guardrail.constant.GuardrailConstant;
import com.korur.oba.apps.kaya.guardrail.dto.request.GuardrailQueryRequest;
import com.korur.oba.apps.kaya.guardrail.entity.Guardrail;

/**
 * Filters of the guardrail list. Every list is limited to one organization, so a version id of another
 * organization matches nothing.
 */
public final class GuardrailSpecification {

    private GuardrailSpecification() {
    }

    private static Specification<Guardrail> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Guardrail> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(GuardrailConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Guardrail> byVersionIds(List<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get(GuardrailConstant.FIELD_VERSION_ID).in(versionIds);
    }

    /**
     * The guardrails of the organization among the requested versions.
     *
     * @param organizationId the organization of the request
     * @param request        the version ids
     * @return the combined filter
     */
    public static Specification<Guardrail> filterBy(UUID organizationId, GuardrailQueryRequest request) {
        return byOrganization(organizationId)
                .and(byVersionIds(request.getVersionId()));
    }
}
