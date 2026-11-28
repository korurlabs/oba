package com.korur.oba.apps.otak.relation.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import static com.korur.oba.apps.otak.relation.constant.service.RelationServiceLogConstant.*;

import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.apps.otak.relation.dto.projection.RelationMailProjection;
import com.korur.oba.apps.otak.relation.dto.projection.RelationMainContactProjection;
import com.korur.oba.apps.otak.relation.dto.projection.RelationNameProjection;
import com.korur.oba.apps.otak.relation.dto.request.RelationQueryRequest;
import com.korur.oba.apps.otak.relation.dto.response.RelationDetailResponse;
import com.korur.oba.apps.otak.relation.dto.response.RelationListResponse;
import com.korur.oba.apps.otak.relation.dto.response.RelationOwnerCandidateResponse;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.helper.RelationOwnerHelper;
import com.korur.oba.apps.otak.relation.mapper.RelationMapper;
import com.korur.oba.apps.otak.relation.repository.RelationRepository;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.apps.otak.relation.specification.RelationSpecification;
import com.korur.oba.apps.otak.relation.validator.RelationValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberKindSummaryProjection;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Implementation of {@link RelationQueryService}, the read side of an organization's
 * relations (leads, customers and suppliers).
 *
 * All methods run within a read-only transaction, so Hibernate skips dirty checking and
 * the entities read here are never written back.
 *
 * Organization scoping: every read names the organization of the request, through
 * {@link RelationSpecification#filterBy}, {@link RelationSpecification#pipelineOf} or
 * {@link RelationValidator#ensureRelationExists(UUID, UUID)}, so a relation of another organization is
 * never listed and reads as not found. The one exception is {@link #getEntityById}, whose caller
 * compares the relation's organization with the request's itself.
 *
 * List shape: a list item shows the relation's owner and its main person and main business
 * contact. Both are read once for the whole list, the owners through
 * {@link RelationOwnerHelper#ownerNames} and the main contacts through
 * {@link RelationRepository#findMainContacts}, and joined in memory by relation id, so a
 * list costs the same number of queries whatever its length. The main contacts are read
 * with a native query in the relation domain because the contact domain already depends
 * on this one; going through its query service would close a bean cycle.
 *
 * Called by {@link com.korur.oba.apps.otak.relation.controller.RelationController}, by
 * {@link com.korur.oba.apps.otak.battlefield.service.impl.BattlefieldQueryServiceImpl} for
 * the pipeline, by the services of the contact and outreach domains through
 * {@link #getEntity}, and by
 * {@link com.korur.oba.shrine.access.service.impl.ResourceAccessQueryServiceImpl} through
 * {@link #getEntityById}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelationQueryServiceImpl implements RelationQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RelationQueryServiceImpl.class);

    // A fixed order, with the id as tie-breaker, keeps pages stable and keeps arbitrary sortBy
    // values from reaching the query.
    private static final Sort LAST_CHANGE_FIRST = Sort.by(Sort.Direction.DESC, RelationConstant.FIELD_UPDATED_AT)
            .and(Sort.by(Sort.Direction.DESC, RelationConstant.FIELD_ID));

    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, RelationConstant.FIELD_NAME)
            .and(Sort.by(Sort.Direction.ASC, RelationConstant.FIELD_ID));

    // Candidates by first name, last name and email, whatever their case; the user id settles ties, so the
    // order is the same on every call.
    private static final Comparator<RelationOwnerCandidateResponse> CANDIDATE_ORDER = Comparator
            .comparing(RelationOwnerCandidateResponse::firstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(RelationOwnerCandidateResponse::lastName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(RelationOwnerCandidateResponse::email, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(RelationOwnerCandidateResponse::userId);

    private final RelationRepository relationRepository;
    private final RelationMapper relationMapper;
    private final RelationValidator relationValidator;
    private final RelationOwnerHelper relationOwnerHelper;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final LoggingService loggingService;
    private final Clock clock;

    /**
     * Lists one page of the organization's relations, last change first, with the list's
     * filters (type, stage, owner, name, contacts, event, campaign) applied by
     * {@link RelationSpecification#filterBy}.
     *
     * Steps: read the page with {@link RelationRepository#findAll}, read the owner names of
     * the page with {@link RelationOwnerHelper#ownerNames} and the main contacts of the page
     * with {@link #mainContacts}, then map each relation with
     * {@link RelationMapper#toListResponse}. The number of queries is fixed, whatever the page
     * size.
     *
     * The order is always {@link #LAST_CHANGE_FIRST}; sortBy and sortDirection of the
     * request are ignored, so no client value reaches the ORDER BY.
     *
     * Called by {@link com.korur.oba.apps.otak.relation.controller.RelationController#listRelations}
     * for the Leads, Customers and Suppliers lists.
     *
     * @param organizationId the organization that owns the relations
     * @param request        the 1-based page, its size and the filters
     * @return the page of list items, each with its owner, main person contact name and
     *         e-mail, and main business contact e-mail; an absent one leaves its fields null
     */
    @Override
    public Page<RelationListResponse> list(UUID organizationId, RelationQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_RELATIONS, organizationId, request);

        // The request's page is 1-based, Spring Data's is 0-based; the sort is fixed.
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(), LAST_CHANGE_FIRST);
        Page<Relation> relations = relationRepository.findAll(
                RelationSpecification.filterBy(organizationId, request), pageable);

        // The owner names and main contacts come once for the whole page instead of once per relation.
        Map<UUID, String> ownerNames = relationOwnerHelper.ownerNames(relations.getContent());
        Map<Boolean, Map<UUID, RelationMainContactProjection>> mainContacts =
                mainContacts(organizationId, relations.getContent());
        // Both keys are always present, so neither map is null; a relation without a main
        // contact of a kind is simply missing from that kind's map.
        Map<UUID, RelationMainContactProjection> mainPersons = mainContacts.get(Boolean.TRUE);
        Map<UUID, RelationMainContactProjection> mainBusinesses = mainContacts.get(Boolean.FALSE);
        // A missing entry gives the mapper null, which leaves that contact's columns empty.
        return relations.map((Relation relation) -> relationMapper.toListResponse(relation,
                relationOwnerHelper.ownerOf(relation, ownerNames), mainPersons.get(relation.getId()),
                mainBusinesses.get(relation.getId())));
    }

    /**
     * Lists every lead and customer of the organization, every stage, by name, unpaged:
     * the battlefield shows the whole pipeline at once. {@link RelationSpecification#pipelineOf}
     * selects the relations that have a stage, which are exactly the leads and customers.
     *
     * Builds the same list items as {@link #list}, with the owners and main contacts read
     * once for all relations through {@link RelationOwnerHelper#ownerNames} and
     * {@link #mainContacts}, and mapped with {@link RelationMapper#toListResponse}.
     *
     * Called by
     * {@link com.korur.oba.apps.otak.battlefield.service.impl.BattlefieldQueryServiceImpl#getBattlefield}.
     *
     * @param organizationId the organization that owns the relations
     * @return the leads and customers in name order, id as tie-breaker; empty when there
     *         are none
     */
    @Override
    public List<RelationListResponse> listPipeline(UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_PIPELINE, organizationId);

        List<Relation> relations = relationRepository.findAll(RelationSpecification.pipelineOf(organizationId), BY_NAME);

        // As in list: owners and main contacts once for all relations, joined by relation id.
        Map<UUID, String> ownerNames = relationOwnerHelper.ownerNames(relations);
        Map<Boolean, Map<UUID, RelationMainContactProjection>> mainContacts = mainContacts(organizationId, relations);
        Map<UUID, RelationMainContactProjection> mainPersons = mainContacts.get(Boolean.TRUE);
        Map<UUID, RelationMainContactProjection> mainBusinesses = mainContacts.get(Boolean.FALSE);
        return relations.stream()
                .map((Relation relation) -> relationMapper.toListResponse(relation,
                        relationOwnerHelper.ownerOf(relation, ownerNames), mainPersons.get(relation.getId()),
                        mainBusinesses.get(relation.getId())))
                .toList();
    }

    /**
     * Lists the members of the organization that the relation owner picker offers: an ACTIVE
     * membership held by an ACTIVE account, of any role, each marked with whether it may
     * open Otak in the organization. A member without Otak is still listed with that mark,
     * so the picker can tell it apart; a relation create or update that names such a member
     * as owner is refused. Agents are left out, because the owner of a relation is a person.
     *
     * Steps: read the members with
     * {@link OrganizationMemberQueryService#listActiveMemberKindSummaries} and drop the agents,
     * ask which of the rest may open Otak with one call to
     * {@link AppAccessQueryService#findUserIdsWithAppAccess}, map each with
     * {@link RelationMapper#toOwnerCandidateResponse} and sort by {@link #CANDIDATE_ORDER}. The
     * number of queries is fixed, whatever the number of members.
     *
     * Called by
     * {@link com.korur.oba.apps.otak.relation.controller.RelationController#listRelationOwnerCandidates}.
     *
     * @param organizationId the organization of the request
     * @return the candidates by first name, last name and email; empty when the organization
     *         has no active person among its members
     */
    @Override
    public List<RelationOwnerCandidateResponse> listOwnerCandidates(UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_OWNER_CANDIDATES, organizationId);

        // Members whose membership already exists now; the clock is injected so tests fix "now".
        List<OrganizationMemberKindSummaryProjection> candidates = new ArrayList<>();
        List<UUID> candidateIds = new ArrayList<>();
        for (OrganizationMemberKindSummaryProjection member
                : organizationMemberQueryService.listActiveMemberKindSummaries(organizationId, Instant.now(clock))) {
            if (UserKind.AGENT.name().equals(member.getKind())) {
                continue;
            }
            candidates.add(member);
            candidateIds.add(member.getUserId());
        }
        // One access read for all candidates instead of one per member.
        Set<UUID> otakUserIds = appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.OTAK,
                candidateIds);
        List<RelationOwnerCandidateResponse> responses = new ArrayList<>();
        for (OrganizationMemberKindSummaryProjection candidate : candidates) {
            responses.add(relationMapper.toOwnerCandidateResponse(candidate,
                    otakUserIds.contains(candidate.getUserId())));
        }
        responses.sort(CANDIDATE_ORDER);
        return responses;
    }

    /**
     * Reads one relation of the organization with its owner, for the relation detail page.
     *
     * Steps: read the relation scoped to the organization through
     * {@link RelationValidator#ensureRelationExists(UUID, UUID)}, resolve its owner's name with
     * {@link RelationOwnerHelper#ownerOf(Relation)} and map with
     * {@link RelationMapper#toDetailResponse}.
     *
     * Called by {@link com.korur.oba.apps.otak.relation.controller.RelationController#getRelation}.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @return the relation with its owner, or a null owner when it has none
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001) when the organization has no relation with the id
     */
    @Override
    public RelationDetailResponse get(UUID organizationId, UUID relationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_RELATION, relationId, organizationId);

        // A relation of another organization answers the same not found as a missing one.
        Relation relation = relationValidator.ensureRelationExists(organizationId, relationId);
        return relationMapper.toDetailResponse(relation, relationOwnerHelper.ownerOf(relation));
    }

    /**
     * Reads one relation entity of the organization, for the services of other Otak domains
     * that work on a relation's children. It is their only way to the relation: they call
     * this query service, never {@link RelationRepository}. Most use it only to check that the
     * relation exists in the organization; a contact create also takes the relation's
     * organization and id from the returned entity.
     *
     * Delegates to {@link RelationValidator#ensureRelationExists(UUID, UUID)}, which reads with
     * {@link RelationRepository#findByIdAndOrganizationId}.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl},
     * {@link com.korur.oba.apps.otak.contact.service.impl.ContactQueryServiceImpl#list},
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl} and
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl}.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @return the relation entity, managed by the caller's transaction
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND}
     *         (OTK_NOT_001) when the organization has no relation with the id
     */
    @Override
    public Relation getEntity(UUID organizationId, UUID relationId) {
        return relationValidator.ensureRelationExists(organizationId, relationId);
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to {@link RelationValidator#ensureRelationExists(UUID)}.
     */
    @Override
    public Relation getEntityById(UUID relationId) {
        return relationValidator.ensureRelationExists(relationId);
    }

    /**
     * {@inheritDoc}
     *
     * One query for all ids; an empty input reads nothing.
     */
    @Override
    public Map<UUID, String> getNames(UUID organizationId, Collection<UUID> relationIds) {
        if (relationIds == null || relationIds.isEmpty()) {
            return Map.of();
        }
        return relationRepository.findNames(organizationId, relationIds).stream()
                .collect(Collectors.toMap(RelationNameProjection::getId, RelationNameProjection::getName));
    }

    /**
     * {@inheritDoc}
     *
     * A HashMap, because a mail without an outreach maps to null, which Collectors.toMap refuses.
     */
    @Override
    public Map<UUID, String> getMailOutreachTitles(UUID organizationId, UUID relationId) {
        Map<UUID, String> titles = new HashMap<>();
        for (RelationMailProjection mail : relationRepository.findMails(organizationId, relationId)) {
            titles.put(mail.getMailId(), mail.getOutreachTitle());
        }
        return titles;
    }

    /**
     * Reads the main person and main business contact of every given relation in one query, split
     * by kind and keyed by relation id: under true the main person contacts, under false the main
     * business contacts; both keys are always present. An empty page sends no query, because an empty
     * IN list is not valid SQL. A relation without a main contact of a kind has no entry for that kind.
     *
     * One query for all relations, through {@link RelationRepository#findMainContacts}, so a list
     * does not cost one contact query per row. Each kind map holds at most one row per relation,
     * because a partial unique index per flag allows one main contact of each kind per relation;
     * that is also why Collectors.toMap, which fails on a duplicate key, is safe here.
     *
     * Called by {@link #list} and {@link #listPipeline}; the two maps feed
     * {@link RelationMapper#toListResponse}.
     *
     * @param organizationId the organization that owns the relations
     * @param relations      the relations of the list; may be empty
     * @return the main contacts by kind (true person, false business), then by relation id
     */
    private Map<Boolean, Map<UUID, RelationMainContactProjection>> mainContacts(UUID organizationId,
                                                                               Collection<Relation> relations) {
        // An empty IN list is not valid SQL, and an empty list has no contacts to read anyway.
        if (relations.isEmpty()) {
            return Map.of(Boolean.TRUE, Map.of(), Boolean.FALSE, Map.of());
        }
        List<UUID> relationIds = relations.stream().map(Relation::getId).toList();
        // partitioningBy always creates both the true and the false key, even when one kind
        // has no rows, so callers never meet a null map.
        return relationRepository.findMainContacts(organizationId, relationIds).stream()
                .collect(Collectors.partitioningBy(
                        (RelationMainContactProjection row) -> Boolean.TRUE.equals(row.getMainPersonContact()),
                        Collectors.toMap(RelationMainContactProjection::getRelationId,
                                (RelationMainContactProjection row) -> row)));
    }
}
