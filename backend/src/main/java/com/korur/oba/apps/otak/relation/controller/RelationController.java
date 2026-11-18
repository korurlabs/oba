package com.korur.oba.apps.otak.relation.controller;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;

import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.constant.controller.RelationControllerLogConstant;
import com.korur.oba.apps.otak.relation.constant.controller.RelationControllerResponseConstant;
import com.korur.oba.apps.otak.relation.dto.request.RelationCreateRequest;
import com.korur.oba.apps.otak.relation.dto.request.RelationQueryRequest;
import com.korur.oba.apps.otak.relation.dto.request.RelationStageRequest;
import com.korur.oba.apps.otak.relation.dto.request.RelationTypeRequest;
import com.korur.oba.apps.otak.relation.dto.request.RelationUpdateRequest;
import com.korur.oba.apps.otak.relation.dto.response.RelationDetailResponse;
import com.korur.oba.apps.otak.relation.dto.response.RelationListResponse;
import com.korur.oba.apps.otak.relation.dto.response.RelationOwnerCandidateResponse;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.apps.otak.relation.service.RelationService;
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * REST controller for the relations of an organization: the companies it sells to,
 * buys from or tries to win. Every member reads and changes them; only an admin
 * deletes one.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/otak/relations")
@Tag(name = "Otak relations", description = "Relations of an organization")
public class RelationController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RelationController.class);

    private final RelationService relationService;
    private final RelationQueryService relationQueryService;
    private final LoggingService loggingService;

    /**
     * Lists one page of the relations of one type, or of every type.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List relations",
            description = "One page of the organization's relations of one type, or of every type and stage when "
                    + "the type is left out, last change first. The stage filter applies to leads and customers; "
                    + "leads at LOST or PARKED show only when it names them. Each row carries the name of the "
                    + "relation's main contact.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "422", description = "Invalid filters, such as a search that is too long "
                    + "(GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<RelationListResponse>>> listRelations(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject RelationQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_LIST_REQUEST);

        Page<RelationListResponse> response = relationQueryService.list(organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_LIST_COMPLETED);

        return ResponseUtil.ok(RelationControllerResponseConstant.RESPONSE_RELATION_LIST_RETRIEVED, response);
    }

    /**
     * Lists the active members of the organization for the relation owner picker, each with whether it may
     * open Otak there, so the picker can show a member without Otak as one that cannot own a relation.
     */
    @GetMapping("/owner-candidates")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List relation owner candidates",
            description = "ACTIVE members with an ACTIVE account of the organization, ordered by first name, last "
                    + "name and email. otakAccess false marks a member that create and update refuse as owner with "
                    + "APP_VAL_002.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<RelationOwnerCandidateResponse>>> listRelationOwnerCandidates(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                RelationControllerLogConstant.RELATION_OWNER_CANDIDATE_LIST_REQUEST);

        List<RelationOwnerCandidateResponse> response = relationQueryService.listOwnerCandidates(organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                RelationControllerLogConstant.RELATION_OWNER_CANDIDATE_LIST_COMPLETED);

        return ResponseUtil.ok(RelationControllerResponseConstant.RESPONSE_RELATION_OWNER_CANDIDATES_RETRIEVED,
                response);
    }

    /**
     * Creates a relation.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create relation",
            description = "Creates a relation of the organization. A lead starts at the given lead stage, else "
                    + "NEW; a customer starts at DIRECT and a supplier has no stage, so neither takes one.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header, or an "
                    + "unknown enum value in the body, such as a country code (GEN_API_001)"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "ICP not found or of another organization "
                    + "(OTK_NOT_004), or event not found or of another organization (OTK_NOT_009)"),
            @ApiResponse(responseCode = "409", description = "Owner is not an active member of the organization "
                    + "(OTK_STA_001), a stage given for a customer or supplier (OTK_STA_002), or CLOSED or DIRECT "
                    + "given for a lead (OTK_STA_006)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001), or the owner "
                    + "may not open Otak in the organization (APP_VAL_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<RelationDetailResponse>> createRelation(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid RelationCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_CREATE_REQUEST);

        RelationDetailResponse response = relationService.create(organizationId, user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_CREATE_COMPLETED);

        return ResponseUtil.created(RelationControllerResponseConstant.RESPONSE_RELATION_CREATED, response);
    }

    /**
     * Reads one relation.
     */
    @GetMapping("/{relationId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get relation", description = "Every field of one relation of the organization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation not found or of another organization "
                    + "(OTK_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<RelationDetailResponse>> getRelation(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID relationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_GET_REQUEST);

        RelationDetailResponse response = relationQueryService.get(organizationId, relationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_GET_COMPLETED);

        return ResponseUtil.ok(RelationControllerResponseConstant.RESPONSE_RELATION_RETRIEVED, response);
    }

    /**
     * Replaces the fields of a relation.
     */
    @PutMapping("/{relationId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update relation",
            description = "Replaces every field of a relation but its type and stage; null clears an optional field.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header, or an "
                    + "unknown enum value in the body, such as a country code (GEN_API_001)"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation not found or of another organization "
                    + "(OTK_NOT_001), ICP not found or of another organization (OTK_NOT_004), or event not "
                    + "found or of another organization (OTK_NOT_009)"),
            @ApiResponse(responseCode = "409", description = "New owner is not an active member of the organization "
                    + "(OTK_STA_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001), or the owner "
                    + "may not open Otak in the organization (APP_VAL_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<RelationDetailResponse>> updateRelation(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID relationId,
            @RequestBody @Valid RelationUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_UPDATE_REQUEST);

        RelationDetailResponse response = relationService.update(organizationId, relationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_UPDATE_COMPLETED);

        return ResponseUtil.ok(RelationControllerResponseConstant.RESPONSE_RELATION_UPDATED, response);
    }

    /**
     * Moves a lead to another stage; CLOSED makes it a customer.
     */
    @PatchMapping("/{relationId}/stage")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Change lead stage",
            description = "Moves a lead to another stage of the battlefield. CLOSED means the lead was won: in the "
                    + "same transaction it becomes a CUSTOMER at stage CLOSED, and the response shows the customer. "
                    + "DIRECT is refused, since it marks a customer that never was a lead.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation not found or of another organization "
                    + "(OTK_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "The relation is not a lead (OTK_STA_002), or the "
                    + "stage is DIRECT (OTK_STA_006)"),
            @ApiResponse(responseCode = "422", description = "Missing or unknown stage (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<RelationDetailResponse>> changeRelationStage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID relationId,
            @RequestBody @Valid RelationStageRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_STAGE_REQUEST);

        RelationDetailResponse response = relationService.changeStage(organizationId, relationId, request.stage());

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_STAGE_COMPLETED);

        return ResponseUtil.ok(RelationControllerResponseConstant.RESPONSE_RELATION_STAGE_CHANGED, response);
    }

    /**
     * Changes the type of a relation, such as a lead that became a customer.
     */
    @PatchMapping("/{relationId}/type")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Change relation type",
            description = "Changes the type of a relation, such as a lead that became a customer, and sets the "
                    + "stage the new type needs: a lead restarts at NEW, a lead that becomes a customer is CLOSED, a "
                    + "supplier that becomes a customer is DIRECT, and a supplier has no stage.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation not found or of another organization "
                    + "(OTK_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "The relation already has this type (OTK_STA_003)"),
            @ApiResponse(responseCode = "422", description = "Missing type (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<RelationDetailResponse>> changeRelationType(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID relationId,
            @RequestBody @Valid RelationTypeRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_TYPE_REQUEST);

        RelationDetailResponse response = relationService.changeType(organizationId, user, relationId, request.type());

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_TYPE_COMPLETED);

        return ResponseUtil.ok(RelationControllerResponseConstant.RESPONSE_RELATION_TYPE_CHANGED, response);
    }

    /**
     * Deletes a relation with its contacts.
     */
    @DeleteMapping("/{relationId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete relation",
            description = "Removes a relation with its contacts; organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Relation not found or of another organization "
                    + "(OTK_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteRelation(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID relationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_DELETE_REQUEST);

        relationService.delete(organizationId, relationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RelationControllerLogConstant.RELATION_DELETE_COMPLETED);

        return ResponseUtil.ok(RelationControllerResponseConstant.RESPONSE_RELATION_DELETED);
    }
}
