package com.korur.oba.apps.otak.relation.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.dto.request.RelationCreateRequest;
import com.korur.oba.apps.otak.relation.dto.request.RelationUpdateRequest;
import com.korur.oba.apps.otak.relation.dto.response.RelationDetailResponse;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service interface for an organization's relations. The caller's role in the
 * organization is checked before these methods run; every method works within that
 * organization, so a relation of another organization is a missing one.
 */
public interface RelationService {

    /**
     * Creates a relation. A lead starts at the given lead stage, else NEW; a customer starts
     * at DIRECT and a supplier has no stage, so neither may be given one.
     *
     * @param organizationId the organization that owns the relation
     * @param user           the authenticated user
     * @param request        the relation creation payload
     * @return the created relation
     */
    RelationDetailResponse create(UUID organizationId, User user, RelationCreateRequest request);

    /**
     * Replaces the fields of a relation; type and stage stay as they are.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @param request        the relation update payload
     * @return the updated relation
     */
    RelationDetailResponse update(UUID organizationId, UUID relationId, RelationUpdateRequest request);

    /**
     * Moves a lead to another lead stage. CLOSED means the lead was won: it becomes a
     * customer at stage CLOSED in the same transaction. DIRECT is refused, since it marks a
     * customer that never was a lead.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     * @param stage          the new stage
     * @return the updated relation, a customer after CLOSED
     */
    RelationDetailResponse changeStage(UUID organizationId, UUID relationId, PipelineStage stage);

    /**
     * Changes the type of a relation and sets the stage the new type needs: a lead restarts
     * at NEW, a lead that becomes a customer is CLOSED (won), a supplier that becomes a
     * customer is DIRECT, and a supplier has no stage.
     *
     * @param organizationId the organization that owns the relation
     * @param user           the authenticated user who changes the type
     * @param relationId     the relation identifier
     * @param type           the new type; must differ from the current one
     * @return the updated relation
     */
    RelationDetailResponse changeType(UUID organizationId, User user, UUID relationId, RelationType type);

    /**
     * Deletes a relation; the database removes its contacts, outreaches and mails with it, and
     * the attachments of the relation and of its mails are removed in the same transaction.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     */
    void delete(UUID organizationId, UUID relationId);
}
