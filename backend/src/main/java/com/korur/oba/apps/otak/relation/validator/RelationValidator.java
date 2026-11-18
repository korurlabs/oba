package com.korur.oba.apps.otak.relation.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.apps.otak.relation.constant.RelationExceptionLogConstant;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.apps.otak.relation.repository.RelationRepository;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Validator for relation lookups, the relation owner, the stage of each type and type changes.
 */
@Component
@RequiredArgsConstructor
public class RelationValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RelationValidator.class);

    private final RelationRepository relationRepository;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessValidator appAccessValidator;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the relation and returns it. A relation of another
     * organization answers the same not found as a missing one, so its id reveals
     * nothing to another tenant.
     *
     * @param organizationId the organization the caller acts in
     * @param relationId     the relation identifier
     * @return the relation
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_RELATION_FOUND} if the
     *         organization has no relation with the id
     */
    public Relation ensureRelationExists(UUID organizationId, UUID relationId) {
        return relationRepository.findByIdAndOrganizationId(relationId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            RelationExceptionLogConstant.RELATION_NOT_FOUND,
                            relationId,
                            organizationId);
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_RELATION_FOUND,
                            Map.of(RelationConstant.FIELD_RELATION_ID, relationId));
                });
    }

    /**
     * Ensures a relation exists in any organization and returns it, for the access check of an attachment
     * that names its relation by id alone. The caller compares the relation's organization with the
     * request's before it shows anything of the relation.
     *
     * @param relationId the relation identifier
     * @return the relation
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_RELATION_FOUND} if no relation has the id
     */
    public Relation ensureRelationExists(UUID relationId) {
        return relationRepository.findById(relationId)
                .orElseThrow(() -> {
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            RelationExceptionLogConstant.RELATION_NOT_FOUND_BY_ID,
                            relationId);
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_RELATION_FOUND,
                            Map.of(RelationConstant.FIELD_RELATION_ID, relationId));
                });
    }

    /**
     * Ensures a relation owner is an ACTIVE member of the organization who may open Otak
     * there, so a relation is never handed to someone outside the tenant or to a member
     * who could not see it. An unknown user gets the same answer as a non-member, so the
     * check reveals nothing about other organizations. The membership is checked first,
     * so a non-member keeps its own code. A null owner means no owner and passes.
     *
     * @param organizationId the organization that owns the relation
     * @param ownerUserId    the owner to set, or null for none
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_OWNER_NOT_ORGANIZATION_MEMBER}
     *         when the user has no ACTIVE membership of the organization
     * @throws ValidationException APP_VAL_002 from {@link AppAccessValidator} when the member
     *         may not open Otak in the organization
     */
    public void ensureOwnerIsOrganizationMember(UUID organizationId, UUID ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        boolean activeMember = organizationMemberQueryService.findMember(organizationId, ownerUserId)
                .map((OrganizationMember member) -> member.getStatus() == OrganizationMemberStatus.ACTIVE)
                .orElse(false);
        if (!activeMember) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    RelationExceptionLogConstant.OWNER_NOT_ORGANIZATION_MEMBER, organizationId, ownerUserId);
            throw new ConflictException(
                    ExceptionDomainOtakError.STA_OWNER_NOT_ORGANIZATION_MEMBER,
                    Map.of(RelationConstant.FIELD_ORGANIZATION_ID, organizationId,
                            RelationConstant.FIELD_OWNER_USER_ID, ownerUserId));
        }
        appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.OTAK, List.of(ownerUserId));
    }

    /**
     * Ensures the relation is a lead, the only type whose stage a user sets.
     *
     * @param relation the relation
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_RELATION_NOT_LEAD} for a
     *         customer or a supplier
     */
    public void ensureRelationIsLead(Relation relation) {
        if (relation.getType() != RelationType.LEAD) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    RelationExceptionLogConstant.RELATION_NOT_LEAD, relation.getId(), relation.getType());
            throw new ConflictException(
                    ExceptionDomainOtakError.STA_RELATION_NOT_LEAD,
                    Map.of(RelationConstant.FIELD_RELATION_ID, relation.getId(),
                            RelationConstant.FIELD_TYPE, relation.getType()));
        }
    }

    /**
     * Ensures a stage given for a new relation belongs to a lead, the only type whose stage
     * a user sets. No stage passes for every type.
     *
     * @param type  the type of the new relation
     * @param stage the requested stage, or null for none
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_RELATION_NOT_LEAD} when a
     *         stage is given for a customer or a supplier
     */
    public void ensureStageOnlyForLead(RelationType type, PipelineStage stage) {
        if (stage != null && type != RelationType.LEAD) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    RelationExceptionLogConstant.STAGE_ON_NON_LEAD, type);
            throw new ConflictException(
                    ExceptionDomainOtakError.STA_RELATION_NOT_LEAD,
                    Map.of(RelationConstant.FIELD_TYPE, type));
        }
    }

    /**
     * Ensures a stage is one the type can have: a lead stage for a lead, CLOSED or DIRECT for
     * a customer, none for a supplier. No stage passes for every type.
     *
     * @param type  the relation type
     * @param stage the requested stage, or null for none
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_STAGE_NOT_FOR_TYPE} when the
     *         type cannot have the stage
     */
    public void ensureStageFitsType(RelationType type, PipelineStage stage) {
        if (stage != null && !stagesOf(type).contains(stage)) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    RelationExceptionLogConstant.STAGE_NOT_FOR_TYPE, type, stage);
            throw new ConflictException(
                    ExceptionDomainOtakError.STA_STAGE_NOT_FOR_TYPE,
                    Map.of(RelationConstant.FIELD_TYPE, type,
                            RelationConstant.FIELD_STAGE, stage));
        }
    }

    /**
     * Refuses a type change to the type the relation already has, which would only reset
     * a lead's stage.
     *
     * @param relation the relation
     * @param type     the requested type
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_RELATION_TYPE_UNCHANGED}
     *         when the relation already has the type
     */
    public void ensureTypeChanges(Relation relation, RelationType type) {
        if (relation.getType() == type) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    RelationExceptionLogConstant.RELATION_TYPE_UNCHANGED, relation.getId(), type);
            throw new ConflictException(
                    ExceptionDomainOtakError.STA_RELATION_TYPE_UNCHANGED,
                    Map.of(RelationConstant.FIELD_RELATION_ID, relation.getId(),
                            RelationConstant.FIELD_TYPE, type));
        }
    }

    /**
     * The stages a relation of the type can have, the same sets the table's check
     * constraint allows.
     */
    private static List<PipelineStage> stagesOf(RelationType type) {
        return switch (type) {
            case LEAD -> PipelineStage.LEAD_STAGES;
            case CUSTOMER -> PipelineStage.CUSTOMER_STAGES;
            case SUPPLIER -> List.of();
        };
    }
}
