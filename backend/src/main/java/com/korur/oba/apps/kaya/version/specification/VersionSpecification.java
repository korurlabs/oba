package com.korur.oba.apps.kaya.version.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.version.constant.VersionConstant;
import com.korur.oba.apps.kaya.version.dto.request.VersionQueryRequest;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;

/**
 * Filters of the version list. Every list is limited to one organization, so a pack id of another
 * organization matches nothing.
 */
public final class VersionSpecification {

    private VersionSpecification() {
    }

    private static Specification<Version> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Version> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(VersionConstant.ATTRIBUTE_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Version> byPack(UUID packId) {
        if (packId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(VersionConstant.ATTRIBUTE_PACK_ID), packId);
    }

    private static Specification<Version> byStatus(VersionStatus status) {
        if (status == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(VersionConstant.ATTRIBUTE_STATUS), status);
    }

    // Every version of the owner a member may read in a history: the released ones, the one in
    // review, and the viewer's own drafts, since another member's draft is still private work.
    private static Specification<Version> visibleTo(UUID viewerId) {
        return (root, query, cb) -> cb.or(
                root.get(VersionConstant.ATTRIBUTE_STATUS).in(VersionStatus.APPROVED, VersionStatus.SUPERSEDED,
                        VersionStatus.IN_REVIEW),
                cb.and(cb.equal(root.get(VersionConstant.ATTRIBUTE_STATUS), VersionStatus.DRAFT),
                        cb.equal(root.get(VersionConstant.ATTRIBUTE_PROPOSED_BY), viewerId)));
    }

    /**
     * The versions of one pack that a member sees in the history of a file.
     *
     * @param organizationId the organization of the request
     * @param packId         the pack
     * @param viewerId       the member, whose own drafts are included
     * @return the combined filter
     */
    public static Specification<Version> historyOf(UUID organizationId, UUID packId, UUID viewerId) {
        return byOrganization(organizationId)
                .and(byPack(packId))
                .and(visibleTo(viewerId));
    }

    /**
     * The versions of the organization, optionally of one pack and one status.
     *
     * @param organizationId the organization of the request
     * @param request        the filters
     * @return the combined filter
     */
    public static Specification<Version> filterBy(UUID organizationId, VersionQueryRequest request) {
        return byOrganization(organizationId)
                .and(byPack(request.getPackId()))
                .and(byStatus(request.getStatus()));
    }
}
