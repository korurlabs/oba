package com.korur.oba.apps.otak.outreach.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.outreach.constant.OutreachConstant;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachQueryRequest;
import com.korur.oba.apps.otak.outreach.entity.Outreach;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;

/**
 * JPA Specification builder for {@link Outreach} entity queries: the filters of the
 * outreach list.
 *
 * Organization scoping: {@link #filterBy} always starts from
 * {@link #belongsToOrganization}, so the list of one organization can never show an
 * outreach of another, whatever filters the request carries.
 *
 * The optional filters return {@link #noOp()} with cb.conjunction() for a null parameter,
 * so they chain correctly with AND and an absent filter matches everything. The
 * organization filter has no no-op.
 *
 * Used by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#list},
 * which passes the result to
 * {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository} through its
 * {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor#findAll} with paging.
 *
 * Kept final with a private constructor, because it only holds static builders.
 *
 * @see com.korur.oba.apps.otak.outreach.repository.OutreachRepository
 * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl
 */
public final class OutreachSpecification {

    /**
     * Prevents instantiation of this static builder class.
     *
     * @throws UnsupportedOperationException always
     */
    private OutreachSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a no-op specification that matches all records. Built on cb.conjunction(),
     * an always-true predicate, rather than a null specification, so it combines with
     * .and() without null checks.
     *
     * @return a specification that matches every outreach
     */
    private static Specification<Outreach> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Filters outreaches owned by the given organization. Unlike the optional filters it
     * has no no-op for null: the organization separates tenants, so a missing id must
     * match nothing rather than every organization's outreaches: a null compared with
     * cb.equal is never true.
     *
     * Called by {@link #filterBy}.
     *
     * @param organizationId the organization whose outreaches match, from the checked request header
     * @return a specification that matches the organization's outreaches only
     */
    public static Specification<Outreach> belongsToOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get(OutreachConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    /**
     * Filters outreaches to one relation. Returns no-op if relationId is null. The relation
     * itself is checked to be of the organization by
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#list}
     * before the query runs; this filter only narrows the rows.
     *
     * @param relationId the relation filter of the request, or null for every relation
     * @return a specification on the relation id, or {@link #noOp()}
     */
    private static Specification<Outreach> relationSpecification(UUID relationId) {
        if (relationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(OutreachConstant.FIELD_RELATION_ID), relationId);
    }

    /**
     * Filters outreaches with one status, for example
     * {@link OutreachStatus#FAILED} to find the outreaches that need another send. Returns
     * no-op if status is null.
     *
     * @param status the status filter of the request, or null for every status
     * @return a specification on the status, or {@link #noOp()}
     */
    private static Specification<Outreach> statusSpecification(OutreachStatus status) {
        if (status == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(OutreachConstant.FIELD_STATUS), status);
    }

    /**
     * Filters outreaches of one campaign. Returns no-op if campaignId is null. A campaign of
     * another organization matches nothing, because the organization filter is ANDed.
     *
     * @param campaignId the campaign filter of the request, or null for every outreach
     * @return a specification on the campaign id, or {@link #noOp()}
     */
    private static Specification<Outreach> campaignSpecification(UUID campaignId) {
        if (campaignId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(OutreachConstant.FIELD_CAMPAIGN_ID), campaignId);
    }

    /**
     * Builds the specification of the outreach list: one organization and the optional
     * relation, status and campaign filters, joined with AND.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#list}.
     *
     * @param organizationId the organization whose outreaches are listed
     * @param request        the list filters
     * @return combined specification with all applicable filters
     */
    public static Specification<Outreach> filterBy(UUID organizationId, OutreachQueryRequest request) {
        return belongsToOrganization(organizationId)
                .and(relationSpecification(request.getRelationId()))
                .and(statusSpecification(request.getStatus()))
                .and(campaignSpecification(request.getCampaignId()));
    }
}
