package com.korur.oba.apps.otak.relation.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.constant.ContactConstant;
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.apps.otak.relation.dto.request.RelationQueryRequest;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * JPA Specification builder for Relation entity queries.
 * <p>
 * The optional filters return noOp() with cb.conjunction() for a null parameter, so
 * they chain correctly with AND. The organization filter has no no-op.
 */
public final class RelationSpecification {

    private RelationSpecification() {
        throw new UnsupportedOperationException(CommonConstant.UTILITY_CLASS_MESSAGE);
    }

    /**
     * Returns a no-op specification that matches all records.
     */
    private static Specification<Relation> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Filters relations owned by the given organization. Unlike the optional filters it has
     * no no-op for null: the organization separates tenants, so a missing id must match
     * nothing rather than every organization's relations.
     */
    public static Specification<Relation> belongsToOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get(RelationConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    /**
     * Filters relations of one type. Returns no-op if {@code type} is null.
     */
    public static Specification<Relation> hasType(RelationType type) {
        if (type == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(RelationConstant.FIELD_TYPE), type);
    }

    /**
     * Filters leads and customers by stage; a stage of another type matches nothing. Without
     * stages a lead list leaves out the inactive stages, so LOST and PARKED show only when
     * asked for, and a customer list keeps every customer. A supplier has no stage, so for
     * a supplier, or no type, the filter is a no-op.
     */
    private static Specification<Relation> byStages(RelationType type, List<PipelineStage> stages) {
        if (type != RelationType.LEAD && type != RelationType.CUSTOMER) {
            return noOp();
        }
        if (stages == null || stages.isEmpty()) {
            return type == RelationType.LEAD
                    ? (root, query, cb) -> cb.not(root.get(RelationConstant.FIELD_STAGE)
                            .in(PipelineStage.INACTIVE_LEAD_STAGES))
                    : noOp();
        }
        return (root, query, cb) -> root.get(RelationConstant.FIELD_STAGE).in(stages);
    }

    /**
     * Filters relations of one owner. Returns no-op if {@code ownerUserId} is null.
     */
    private static Specification<Relation> byOwner(UUID ownerUserId) {
        if (ownerUserId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(RelationConstant.FIELD_OWNER_USER_ID), ownerUserId);
    }

    /**
     * Case-insensitive search on the relation name. Returns no-op if {@code keyword} is null or blank.
     */
    private static Specification<Relation> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        String pattern = RelationConstant.LIKE_WILDCARD + keyword.trim().toLowerCase() + RelationConstant.LIKE_WILDCARD;
        return (root, query, cb) -> cb.like(cb.lower(root.get(RelationConstant.FIELD_NAME)), pattern);
    }

    /**
     * Filters relations by whether they have a contact, so that a caller can find the
     * relations that still need a person to talk to. Returns no-op if {@code hasContacts}
     * is null. A contact always carries its relation's organization, so the relation id
     * alone ties the subquery to the relation.
     */
    private static Specification<Relation> byContacts(Boolean hasContacts) {
        if (hasContacts == null) {
            return noOp();
        }
        return (root, query, cb) -> {
            Subquery<UUID> contact = query.subquery(UUID.class);
            Root<Contact> contactRoot = contact.from(Contact.class);
            contact.select(contactRoot.get(ContactConstant.FIELD_ID)).where(
                    cb.equal(contactRoot.get(ContactConstant.FIELD_RELATION_ID), root.get(RelationConstant.FIELD_ID)));
            return hasContacts ? cb.exists(contact) : cb.not(cb.exists(contact));
        };
    }

    /**
     * Filters relations linked to one event. Returns no-op if {@code eventId} is null.
     */
    private static Specification<Relation> byEvent(UUID eventId) {
        if (eventId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(RelationConstant.FIELD_EVENT_ID), eventId);
    }

    /**
     * Filters relations linked to one campaign. Returns no-op if {@code campaignId} is null.
     */
    private static Specification<Relation> byCampaign(UUID campaignId) {
        if (campaignId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(RelationConstant.FIELD_CAMPAIGN_ID), campaignId);
    }

    /**
     * Builds the specification of the relations list: one organization, and the optional
     * type, stage, owner, name, contact, event and campaign filters. Without a type every relation
     * of the organization matches, whatever its stage.
     *
     * @param organizationId the organization whose relations are listed
     * @param request        the list filters
     * @return combined specification with all applicable filters
     */
    public static Specification<Relation> filterBy(UUID organizationId, RelationQueryRequest request) {
        return belongsToOrganization(organizationId)
                .and(hasType(request.getType()))
                .and(byStages(request.getType(), request.getStage()))
                .and(byOwner(request.getOwnerUserId()))
                .and(searchSpecification(request.getSearch()))
                .and(byContacts(request.getHasContacts()))
                .and(byEvent(request.getEventId()))
                .and(byCampaign(request.getCampaignId()));
    }

    /**
     * Builds the specification of every lead and customer of one organization, every stage.
     * The check constraint gives exactly leads and customers a stage, so a stage alone
     * selects them and leaves the suppliers out.
     *
     * @param organizationId the organization whose relations are listed
     * @return the pipeline specification
     */
    public static Specification<Relation> pipelineOf(UUID organizationId) {
        return belongsToOrganization(organizationId)
                .and((root, query, cb) -> cb.isNotNull(root.get(RelationConstant.FIELD_STAGE)));
    }
}
