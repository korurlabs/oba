package com.korur.oba.apps.otak.relation.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.relation.dto.projection.RelationMainContactProjection;
import com.korur.oba.apps.otak.relation.dto.request.RelationCreateRequest;
import com.korur.oba.apps.otak.relation.dto.request.RelationUpdateRequest;
import com.korur.oba.apps.otak.relation.dto.response.RelationDetailResponse;
import com.korur.oba.apps.otak.relation.dto.response.RelationListResponse;
import com.korur.oba.apps.otak.relation.dto.response.RelationOwnerCandidateResponse;
import com.korur.oba.apps.otak.relation.dto.response.RelationOwnerResponse;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;

/**
 * MapStruct mapper between {@link Relation} and its request, response and projection
 * types. The generated implementation is a Spring bean with constructor injection.
 *
 * The mapper copies plain fields only. What needs a rule or a lookup is resolved before it
 * is called: the organization comes from the checked X-Organization-ID header, the stage
 * and the type change only through their own operations, the owner is resolved to a name
 * by {@link com.korur.oba.apps.otak.relation.helper.RelationOwnerHelper}, and the main
 * contacts are read in one query per list by
 * {@link com.korur.oba.apps.otak.relation.repository.RelationRepository#findMainContacts}.
 * The relation domain has no contact entity, so the main contacts arrive as
 * {@link RelationMainContactProjection} rows rather than contacts.
 *
 * Used by {@link com.korur.oba.apps.otak.relation.service.impl.RelationServiceImpl} for the writes and by
 * {@link com.korur.oba.apps.otak.relation.service.impl.RelationQueryServiceImpl} for the reads.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface RelationMapper {

    /**
     * Maps a relation creation request to a new entity.
     *
     * The service sets the organization from the checked request header and the stage
     * from the request, or from the type when none is given; the request's organization is
     * never trusted, so a relation can not be written into another organization.
     *
     * Called by {@link com.korur.oba.apps.otak.relation.service.impl.RelationServiceImpl#create}, after the stage, owner, ICP and
     * event checks.
     *
     * @param request the validated create payload
     * @return a new, unsaved relation without organization or stage
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "stage", ignore = true)
    Relation toEntity(RelationCreateRequest request);

    /**
     * Writes every field of an update request onto the relation; null clears an optional field.
     * This is the full replace of the PUT: no field is skipped when null, so a client sends
     * the whole relation.
     *
     * Type and stage change only through their own operations,
     * {@link com.korur.oba.apps.otak.relation.service.impl.RelationServiceImpl#changeType} and
     * {@link com.korur.oba.apps.otak.relation.service.impl.RelationServiceImpl#changeStage}, because each change has its own rules
     * (a stage must fit the type, a type change sets the stage the new type needs).
     *
     * Called by {@link com.korur.oba.apps.otak.relation.service.impl.RelationServiceImpl#update}, after the owner, ICP and event checks.
     *
     * @param relation the managed relation to change in place
     * @param request  the validated update payload
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "stage", ignore = true)
    void updateEntity(@MappingTarget Relation relation, RelationUpdateRequest request);

    /**
     * Maps a relation, its resolved owner and its main person and main business contact to a list item;
     * a null contact leaves its fields null.
     *
     * Person versus business contacts: the list shows the name and email of the main person
     * contact (the one to talk to first) and only the email of the main business contact
     * (the inbox to write to), so the two arrive as separate arguments. A relation has at
     * most one main contact of each kind, which is why one projection per kind is enough;
     * a relation without a contact of a kind gets null for that kind.
     *
     * Called by {@link com.korur.oba.apps.otak.relation.service.impl.RelationQueryServiceImpl#list} and
     * {@link com.korur.oba.apps.otak.relation.service.impl.RelationQueryServiceImpl#listPipeline}, with the owners from
     * {@link com.korur.oba.apps.otak.relation.helper.RelationOwnerHelper#ownerOf(Relation, java.util.Map)}
     * and the main contacts from
     * {@link com.korur.oba.apps.otak.relation.repository.RelationRepository#findMainContacts}.
     *
     * @param relation     the stored relation
     * @param owner        the resolved owner, or null when the relation has none
     * @param mainPerson   the main person contact, or null when the relation has none
     * @param mainBusiness the main business contact, or null when the relation has none
     * @return the list item
     */
    @Mapping(target = "id", source = "relation.id")
    @Mapping(target = "name", source = "relation.name")
    @Mapping(target = "owner", source = "owner")
    @Mapping(target = "mainPersonContactName", source = "mainPerson.name")
    @Mapping(target = "mainPersonContactEmail", source = "mainPerson.email")
    @Mapping(target = "mainBusinessContactEmail", source = "mainBusiness.email")
    RelationListResponse toListResponse(Relation relation, RelationOwnerResponse owner,
                                        RelationMainContactProjection mainPerson,
                                        RelationMainContactProjection mainBusiness);

    /**
     * Maps a relation and its resolved owner to the detail response. The detail carries no
     * contacts: a client reads them with
     * {@link com.korur.oba.apps.otak.contact.controller.ContactController#listContacts}.
     *
     * Called by {@link com.korur.oba.apps.otak.relation.service.impl.RelationQueryServiceImpl#get} and by
     * {@link com.korur.oba.apps.otak.relation.service.impl.RelationServiceImpl#create}, {@link com.korur.oba.apps.otak.relation.service.impl.RelationServiceImpl#update},
     * {@link com.korur.oba.apps.otak.relation.service.impl.RelationServiceImpl#changeStage} and
     * {@link com.korur.oba.apps.otak.relation.service.impl.RelationServiceImpl#changeType}, each with the owner from
     * {@link com.korur.oba.apps.otak.relation.helper.RelationOwnerHelper#ownerOf(Relation)}.
     *
     * @param relation the stored relation
     * @param owner    the resolved owner, or null when the relation has none
     * @return the detail response
     */
    @Mapping(target = "id", source = "relation.id")
    @Mapping(target = "name", source = "relation.name")
    @Mapping(target = "owner", source = "owner")
    RelationDetailResponse toDetailResponse(Relation relation, RelationOwnerResponse owner);

    /**
     * Maps an active member of the organization to an entry of the owner picker, with whether the member
     * may open Otak there.
     *
     * Called by {@link com.korur.oba.apps.otak.relation.service.impl.RelationQueryServiceImpl#listOwnerCandidates}.
     *
     * @param member     the active member of the organization
     * @param otakAccess whether the member may open Otak in the organization
     * @return the owner picker entry
     */
    @Mapping(target = "userId", source = "member.userId")
    @Mapping(target = "email", source = "member.email")
    @Mapping(target = "firstName", source = "member.firstName")
    @Mapping(target = "lastName", source = "member.lastName")
    @Mapping(target = "role", source = "member.role")
    @Mapping(target = "otakAccess", source = "otakAccess")
    RelationOwnerCandidateResponse toOwnerCandidateResponse(OrganizationMemberSummaryProjection member,
                                                            boolean otakAccess);
}
