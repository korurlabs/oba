package com.korur.oba.apps.otak.icp.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.icp.constant.IcpConstant;
import com.korur.oba.apps.otak.icp.dto.request.IcpQueryRequest;
import com.korur.oba.apps.otak.icp.entity.Icp;

/**
 * JPA Specification builder for Icp entity queries.
 *
 * The optional filters return noOp() with cb.conjunction() for a null parameter, so
 * they chain correctly with AND. The organization filter has no no-op.
 */
public final class IcpSpecification {

    private IcpSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a no-op specification that matches all records.
     */
    private static Specification<Icp> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Filters ICPs owned by the given organization. Unlike the optional filters it has no
     * no-op for null: the organization separates tenants, so a missing id must match
     * nothing rather than every organization's ICPs.
     */
    public static Specification<Icp> belongsToOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get(IcpConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    /**
     * Case-insensitive search on the ICP name. Returns no-op if {@code keyword} is null or blank.
     */
    private static Specification<Icp> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(IcpConstant.FIELD_NAME)), pattern);
    }

    /**
     * Builds the specification of the ICP list: one organization and the optional name filter.
     *
     * @param organizationId the organization whose ICPs are listed
     * @param request        the list filters
     * @return combined specification with all applicable filters
     */
    public static Specification<Icp> filterBy(UUID organizationId, IcpQueryRequest request) {
        return belongsToOrganization(organizationId)
                .and(searchSpecification(request.getSearch()));
    }
}
