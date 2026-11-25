package com.korur.oba.apps.otak.campaign.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignQueryRequest;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;

/**
 * JPA Specification builder for Campaign entity queries.
 *
 * The optional filters return noOp() with cb.conjunction() for a null parameter, so they
 * chain correctly with AND. The organization filter has no no-op.
 */
public final class CampaignSpecification {

    private CampaignSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a no-op specification that matches all records.
     */
    private static Specification<Campaign> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Filters campaigns owned by the given organization. Unlike the optional filters it has
     * no no-op for null: the organization separates tenants, so a missing id must match
     * nothing rather than every organization's campaigns.
     */
    public static Specification<Campaign> belongsToOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get(CampaignConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    /**
     * Keeps the campaigns with the status. Returns no-op if {@code status} is null.
     */
    private static Specification<Campaign> byStatus(CampaignStatus status) {
        if (status == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(CampaignConstant.FIELD_STATUS), status);
    }

    /**
     * Keeps the campaigns on the channel. Returns no-op if {@code channel} is null.
     */
    private static Specification<Campaign> byChannel(CampaignChannel channel) {
        if (channel == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(CampaignConstant.FIELD_CHANNEL), channel);
    }

    /**
     * Case-insensitive search on the campaign name. Returns no-op if {@code keyword} is null or blank.
     */
    private static Specification<Campaign> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(CampaignConstant.FIELD_NAME)), pattern);
    }

    /**
     * Builds the specification of the campaign list: one organization, the optional status,
     * channel and name filters.
     *
     * @param organizationId the organization whose campaigns are listed
     * @param request        the list filters
     * @return combined specification with all applicable filters
     */
    public static Specification<Campaign> filterBy(UUID organizationId, CampaignQueryRequest request) {
        return belongsToOrganization(organizationId)
                .and(byStatus(request.getStatus()))
                .and(byChannel(request.getChannel()))
                .and(searchSpecification(request.getSearch()));
    }
}
