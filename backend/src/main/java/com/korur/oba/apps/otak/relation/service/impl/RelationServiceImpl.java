package com.korur.oba.apps.otak.relation.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.relation.constant.service.RelationServiceLogConstant.*;

import com.korur.oba.apps.otak.event.service.EventQueryService;
import com.korur.oba.apps.otak.icp.service.IcpQueryService;
import com.korur.oba.apps.otak.mail.service.MailQueryService;
import com.korur.oba.apps.otak.relation.dto.request.RelationCreateRequest;
import com.korur.oba.apps.otak.relation.dto.request.RelationUpdateRequest;
import com.korur.oba.apps.otak.relation.dto.response.RelationDetailResponse;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.apps.otak.relation.helper.RelationOwnerHelper;
import com.korur.oba.apps.otak.relation.mapper.RelationMapper;
import com.korur.oba.apps.otak.relation.repository.RelationRepository;
import com.korur.oba.apps.otak.relation.service.RelationService;
import com.korur.oba.apps.otak.relation.validator.RelationValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;
import com.korur.oba.shrine.document.service.document.DocumentService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

/**
 * Implements writes to an organization's relations.
 *
 * The organization role is checked once per request by the access interceptor. Every
 * relation is read and written within the request's organization, so a relation of
 * another organization is a missing one.
 */
@Service
@RequiredArgsConstructor
public class RelationServiceImpl implements RelationService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RelationServiceImpl.class);

    private final RelationRepository relationRepository;
    private final RelationMapper relationMapper;
    private final RelationValidator relationValidator;
    private final RelationOwnerHelper relationOwnerHelper;
    private final IcpQueryService icpQueryService;
    private final EventQueryService eventQueryService;
    private final MailQueryService mailQueryService;
    private final AttachmentService attachmentService;
    private final DocumentService documentService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public RelationDetailResponse create(UUID organizationId, User user, RelationCreateRequest request) {
        relationValidator.ensureStageOnlyForLead(request.type(), request.stage());
        relationValidator.ensureStageFitsType(request.type(), request.stage());
        relationValidator.ensureOwnerIsOrganizationMember(organizationId, request.ownerUserId());
        ensureIcpOfOrganization(organizationId, request.icpId());
        ensureEventOfOrganization(organizationId, request.eventId());

        Relation relation = relationMapper.toEntity(request);
        relation.setOrganizationId(organizationId);
        relation.setStage(initialStage(request.type(), request.stage()));
        relation = relationRepository.save(relation);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RELATION_CREATED,
                relation.getId(), organizationId, relation.getType(), user.getId());

        return relationMapper.toDetailResponse(relation, relationOwnerHelper.ownerOf(relation));
    }

    @Override
    @Transactional
    public RelationDetailResponse update(UUID organizationId, UUID relationId, RelationUpdateRequest request) {
        Relation relation = relationValidator.ensureRelationExists(organizationId, relationId);

        // Only a new owner is checked: an owner who left the organization stays on the
        // relation until someone picks another, and does not block editing the rest.
        if (!Objects.equals(relation.getOwnerUserId(), request.ownerUserId())) {
            relationValidator.ensureOwnerIsOrganizationMember(organizationId, request.ownerUserId());
        }
        ensureIcpOfOrganization(organizationId, request.icpId());
        ensureEventOfOrganization(organizationId, request.eventId());

        relationMapper.updateEntity(relation, request);
        // Flushed here so the response carries the new last-change time.
        relation = relationRepository.saveAndFlush(relation);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RELATION_UPDATED, relationId, organizationId);

        return relationMapper.toDetailResponse(relation, relationOwnerHelper.ownerOf(relation));
    }

    @Override
    @Transactional
    public RelationDetailResponse changeStage(UUID organizationId, UUID relationId, PipelineStage stage) {
        Relation relation = relationValidator.ensureRelationExists(organizationId, relationId);
        relationValidator.ensureRelationIsLead(relation);
        boolean won = stage == PipelineStage.CLOSED;
        if (!won) {
            relationValidator.ensureStageFitsType(relation.getType(), stage);
        }

        PipelineStage previousStage = relation.getStage();
        // A won lead becomes a customer in the same statement as its stage change, so the
        // check constraint never sees a lead at CLOSED.
        if (won) {
            relation.setType(RelationType.CUSTOMER);
        }
        relation.setStage(stage);
        relation = relationRepository.saveAndFlush(relation);

        if (won) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RELATION_CLOSED_AS_CUSTOMER, relationId, previousStage);
        } else {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RELATION_STAGE_CHANGED, relationId, previousStage, stage);
        }

        return relationMapper.toDetailResponse(relation, relationOwnerHelper.ownerOf(relation));
    }

    @Override
    @Transactional
    public RelationDetailResponse changeType(UUID organizationId, User user, UUID relationId, RelationType type) {
        Relation relation = relationValidator.ensureRelationExists(organizationId, relationId);
        relationValidator.ensureTypeChanges(relation, type);

        RelationType previousType = relation.getType();
        relation.setType(type);
        // Type and stage are written in one statement: the table holds each type to its own stages.
        relation.setStage(stageAfterTypeChange(previousType, type));
        relation = relationRepository.saveAndFlush(relation);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RELATION_TYPE_CHANGED,
                relationId, previousType, type, user.getId());

        return relationMapper.toDetailResponse(relation, relationOwnerHelper.ownerOf(relation));
    }

    /**
     * Deletes the relation and then its documents and the attachments of the relation and of
     * each of its mails.
     *
     * The database removes the relation's contacts, outreaches and mails with the row, but an
     * attachment row names its parent only by type and id, without a foreign key, so it is
     * removed here, and so is a document, which names its owner the same way. The mail ids are read before the delete, because afterwards the mails are
     * gone. The relation row is flushed first so that a foreign key failure stops the delete
     * before any attachment row is touched. The attachment rows go in this transaction; storage
     * then deletes, best effort, each blob that no remaining row holds.
     *
     * @param organizationId the organization that owns the relation
     * @param relationId     the relation identifier
     */
    @Override
    @Transactional
    public void delete(UUID organizationId, UUID relationId) {
        Relation relation = relationValidator.ensureRelationExists(organizationId, relationId);
        List<UUID> mailIds = mailQueryService.getIdsByRelation(organizationId, relationId);

        relationRepository.delete(relation);
        relationRepository.flush();

        documentService.deleteDocumentsByEntity(DocumentEntityType.RELATION, relationId);
        attachmentService.deleteAttachmentsByEntity(StorageEntityType.RELATION, relationId);
        for (UUID mailId : mailIds) {
            attachmentService.deleteAttachmentsByEntity(StorageEntityType.MAIL, mailId);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RELATION_DELETED, relationId, organizationId);
    }

    /**
     * The stage of a new relation: a lead starts at the given stage or NEW, a customer that
     * never was a lead at DIRECT, and a supplier has none.
     */
    private static PipelineStage initialStage(RelationType type, PipelineStage requestedStage) {
        return switch (type) {
            case LEAD -> Objects.requireNonNullElse(requestedStage, PipelineStage.NEW);
            case CUSTOMER -> PipelineStage.DIRECT;
            case SUPPLIER -> null;
        };
    }

    /**
     * The stage after a type change: a lead restarts at NEW, a lead that becomes a customer
     * was won (CLOSED), a supplier that becomes a customer never was a lead (DIRECT), and a
     * supplier has none.
     */
    private static PipelineStage stageAfterTypeChange(RelationType from, RelationType to) {
        return switch (to) {
            case LEAD -> PipelineStage.NEW;
            case CUSTOMER -> from == RelationType.LEAD ? PipelineStage.CLOSED : PipelineStage.DIRECT;
            case SUPPLIER -> null;
        };
    }

    /**
     * Refuses an ICP that is unknown or belongs to another organization, so a relation
     * never links to a segment its organization cannot see. No ICP is always allowed.
     */
    private void ensureIcpOfOrganization(UUID organizationId, UUID icpId) {
        if (icpId != null) {
            icpQueryService.ensureExists(organizationId, icpId);
        }
    }

    /**
     * Refuses an event that is unknown or belongs to another organization, so a relation
     * never links to an event its organization cannot see. No event is always allowed.
     */
    private void ensureEventOfOrganization(UUID organizationId, UUID eventId) {
        if (eventId != null) {
            eventQueryService.ensureExists(organizationId, eventId);
        }
    }
}
