package com.korur.oba.apps.otak.relation.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.dto.request.RelationQueryRequest;
import com.korur.oba.apps.otak.relation.dto.response.RelationDetailResponse;
import com.korur.oba.apps.otak.relation.dto.response.RelationListResponse;
import com.korur.oba.apps.otak.relation.dto.response.RelationOwnerCandidateResponse;
import com.korur.oba.apps.otak.relation.entity.Relation;

/**
 * Read service interface for an organization's relations.
 */
public interface RelationQueryService {

    /**
     * Lists one page of the organization's relations of one type, last change first.
     *
     * @param organizationId the organization that owns the relations
     * @param request        the paging and filters
     * @return the page of relations
     */
    Page<RelationListResponse> list(UUID organizationId, RelationQueryRequest request);

    /**
     * Every lead and customer of the organization, every stage, by name.
     *
     * @param organizationId the organization that owns the relations
     * @return the leads and customers in name order
     */
    List<RelationListResponse> listPipeline(UUID organizationId);

    /**
     * Lists the members of the organization that the relation owner picker offers: an ACTIVE
     * membership held by an ACTIVE account, of any role, ordered by first name, last name and
     * email. Each says whether it may open Otak in the organization, read for all members with
     * one query; a create or update that names one without Otak as owner is refused.
     *
     * @param organizationId the organization of the request
     * @return the candidates; empty when the organization has no active member
     */
    List<RelationOwnerCandidateResponse> listOwnerCandidates(UUID organizationId);

    /**
     * Reads one relation of the organization.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @return the relation
     */
    RelationDetailResponse get(UUID organizationId, UUID relationId);

    /**
     * Reads one relation entity of the organization, for the services of its contacts.
     * A relation of another organization is not found.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @return the relation entity
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException OTK_NOT_001
     *         when the organization has no relation with the id
     */
    Relation getEntity(UUID organizationId, UUID relationId);

    /**
     * Reads one relation entity by id alone, in any organization, for the access check of an attachment whose
     * parent is a relation. The caller must compare the relation's organization with the request's before it
     * uses the relation.
     *
     * @param relationId the relation identifier
     * @return the relation entity
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException OTK_NOT_001
     *         when no relation has the id
     */
    Relation getEntityById(UUID relationId);

    /**
     * Reads the names of the given relations of the organization in one query, for the
     * relation column of a page of documents. A relation of another organization or a deleted
     * one is absent from the result.
     *
     * Called by {@link com.korur.oba.shrine.document.mapper.document.DocumentMapperHelper#relationNames}.
     *
     * @param organizationId the organization
     * @param relationIds    the relations
     * @return the name per relation found; empty for no ids
     */
    Map<UUID, String> getNames(UUID organizationId, Collection<UUID> relationIds);

    /**
     * Reads the mails of a relation with the title of the outreach that sends each one, in one
     * query. No access check runs here; the caller has checked READ on the relation.
     *
     * Called by {@link com.korur.oba.apps.otak.relation.service.impl.RelationAttachmentQueryServiceImpl}
     * and {@link com.korur.oba.shrine.document.mapper.document.DocumentMapperHelper#outreachTitles}.
     *
     * @param organizationId the organization
     * @param relationId     the relation
     * @return the outreach title per mail id, null for a mail no outreach names; empty when the relation
     *         has no mails
     */
    Map<UUID, String> getMailOutreachTitles(UUID organizationId, UUID relationId);
}
