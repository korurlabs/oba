package com.korur.oba.apps.otak.outreach.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.constant.ContactConstant;
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.outreach.constant.OutreachConstant;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachQueryRequest;
import com.korur.oba.apps.otak.outreach.entity.Outreach;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;
import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.dojo.constant.CommonConstant;

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
        throw new UnsupportedOperationException(CommonConstant.UTILITY_CLASS_MESSAGE);
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
     * Filters outreaches through one channel. Returns no-op if channel is null.
     *
     * @param channel the channel filter of the request, or null for every channel
     * @return a specification on the outreach type, or {@link #noOp()}
     */
    private static Specification<Outreach> channelSpecification(OutreachType channel) {
        if (channel == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(OutreachConstant.FIELD_TYPE), channel);
    }

    /**
     * Filters outreaches sent from one mail sender, or whose failed send last tried it.
     * Returns no-op if mailSenderId is null. A draft has no sender, so it never matches, and a
     * sender of another organization matches nothing, because the organization filter is ANDed.
     *
     * @param mailSenderId the sender filter of the request, or null for every outreach
     * @return a specification on the mail sender id, or {@link #noOp()}
     */
    private static Specification<Outreach> mailSenderSpecification(UUID mailSenderId) {
        if (mailSenderId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(OutreachConstant.FIELD_MAIL_SENDER_ID), mailSenderId);
    }

    /**
     * Case-insensitive search on the outreach title, the name and mail address of its
     * contact, and the name of its relation; a match on any of them keeps the row. Returns
     * no-op if keyword is null or blank.
     *
     * The outreach holds the contact and the relation as plain ids, so each is matched in a
     * correlated EXISTS subquery on its id rather than through a join; a subquery also keeps
     * one row per outreach, so paging counts stay right. An outreach whose contact was
     * deleted still matches on its title and relation.
     *
     * @param keyword the search of the request, trimmed here, or null or blank for none
     * @return a specification that matches any of the four values, or {@link #noOp()}
     */
    private static Specification<Outreach> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        String pattern = OutreachConstant.LIKE_WILDCARD + keyword.trim().toLowerCase()
                + OutreachConstant.LIKE_WILDCARD;
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get(OutreachConstant.FIELD_TITLE)), pattern),
                cb.exists(contactMatches(root, query, cb, pattern)),
                cb.exists(relationMatches(root, query, cb, pattern)));
    }

    /**
     * Subquery of the outreach's contact when its name or mail address matches the pattern.
     * A contact without a mail address can still match on its name.
     *
     * Called by {@link #searchSpecification}.
     *
     * @param root    the outreach root the subquery is correlated with
     * @param query   the outer query that owns the subquery
     * @param cb      the criteria builder of the outer query
     * @param pattern the lower-case LIKE pattern of the search
     * @return the subquery of the matching contact id
     */
    private static Subquery<UUID> contactMatches(Root<Outreach> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                                 String pattern) {
        Subquery<UUID> contact = query.subquery(UUID.class);
        Root<Contact> contactRoot = contact.from(Contact.class);
        Predicate nameOrEmail = cb.or(
                cb.like(cb.lower(contactRoot.get(ContactConstant.FIELD_NAME)), pattern),
                cb.like(cb.lower(contactRoot.get(OutreachConstant.FIELD_CONTACT_EMAIL)), pattern));
        contact.select(contactRoot.get(ContactConstant.FIELD_ID)).where(
                cb.equal(contactRoot.get(ContactConstant.FIELD_ID), root.get(OutreachConstant.FIELD_CONTACT_ID)),
                nameOrEmail);
        return contact;
    }

    /**
     * Subquery of the outreach's relation when its name matches the pattern.
     *
     * Called by {@link #searchSpecification}.
     *
     * @param root    the outreach root the subquery is correlated with
     * @param query   the outer query that owns the subquery
     * @param cb      the criteria builder of the outer query
     * @param pattern the lower-case LIKE pattern of the search
     * @return the subquery of the matching relation id
     */
    private static Subquery<UUID> relationMatches(Root<Outreach> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                                  String pattern) {
        Subquery<UUID> relation = query.subquery(UUID.class);
        Root<Relation> relationRoot = relation.from(Relation.class);
        relation.select(relationRoot.get(RelationConstant.FIELD_ID)).where(
                cb.equal(relationRoot.get(RelationConstant.FIELD_ID), root.get(OutreachConstant.FIELD_RELATION_ID)),
                cb.like(cb.lower(relationRoot.get(RelationConstant.FIELD_NAME)), pattern));
        return relation;
    }

    /**
     * Builds the specification of the outreach list: one organization and the optional
     * relation, status, campaign, channel, mail sender and search filters, joined with AND.
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
                .and(campaignSpecification(request.getCampaignId()))
                .and(channelSpecification(request.getChannel()))
                .and(mailSenderSpecification(request.getMailSenderId()))
                .and(searchSpecification(request.getSearch()));
    }
}
