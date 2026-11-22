package com.korur.oba.shrine.legalentity.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.legalentity.constant.controller.LegalEntityControllerLogConstant;
import com.korur.oba.shrine.legalentity.constant.controller.LegalEntityControllerResponseConstant;
import com.korur.oba.shrine.legalentity.dto.request.LegalEntityCreateRequest;
import com.korur.oba.shrine.legalentity.dto.request.LegalEntityUpdateRequest;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityDetailResponse;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityListResponse;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;
import com.korur.oba.shrine.legalentity.service.LegalEntityService;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * REST endpoints of the legal entities of an organization, the companies that issue its
 * documents, under /legal-entities.
 *
 * The organization comes from the X-Organization-ID header, which
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} checks before
 * any handler runs. Every member reads (MEMBER); only organization admins and owners write
 * (ADMIN). Legal entities belong to no app, so no app grant is needed. A legal entity of
 * another organization answers 404, like a missing one.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/legal-entities")
@Tag(name = "Legal Entities", description = "The companies of an organization that issue its documents")
public class LegalEntityController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LegalEntityController.class);

    private final LegalEntityService legalEntityService;
    private final LegalEntityQueryService legalEntityQueryService;
    private final LoggingService loggingService;

    /**
     * Lists every legal entity of the organization, sorted by short name.
     *
     * @param user           the signed-in member, resolved by {@link GetUser}
     * @param organizationId the organization from the checked header
     * @return the list items, not paged
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List legal entities",
            description = "Every legal entity of the organization, sorted by short name with case ignored. Not paged.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<LegalEntityListResponse>>> listLegalEntities(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                LegalEntityControllerLogConstant.LEGAL_ENTITY_LIST_REQUEST);

        List<LegalEntityListResponse> response = legalEntityQueryService.list(organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                LegalEntityControllerLogConstant.LEGAL_ENTITY_LIST_COMPLETED);

        return ResponseUtil.ok(LegalEntityControllerResponseConstant.RESPONSE_LEGAL_ENTITY_LIST_RETRIEVED, response);
    }

    /**
     * Reads one legal entity of the organization with every field.
     *
     * @param user           the signed-in member, resolved by {@link GetUser}
     * @param organizationId the organization from the checked header
     * @param legalEntityId  the legal entity identifier
     * @return the detail
     */
    @GetMapping("/{legalEntityId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get legal entity", description = "Every field of one legal entity of the organization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Legal entity not found or of another organization "
                    + "(LGE_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<LegalEntityDetailResponse>> getLegalEntity(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID legalEntityId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                LegalEntityControllerLogConstant.LEGAL_ENTITY_GET_REQUEST);

        LegalEntityDetailResponse response = legalEntityQueryService.get(organizationId, legalEntityId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                LegalEntityControllerLogConstant.LEGAL_ENTITY_GET_COMPLETED);

        return ResponseUtil.ok(LegalEntityControllerResponseConstant.RESPONSE_LEGAL_ENTITY_RETRIEVED, response);
    }

    /**
     * Creates a legal entity of the organization.
     *
     * @param user           the signed-in admin, resolved by {@link GetUser}
     * @param organizationId the organization from the checked header
     * @param request        the fields of the new legal entity
     * @return the stored legal entity, with status 201
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Create legal entity",
            description = "Creates a legal entity of the organization. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "409", description = "Another legal entity of the organization has this "
                    + "short name, case ignored (LGE_STA_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<LegalEntityDetailResponse>> createLegalEntity(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid LegalEntityCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                LegalEntityControllerLogConstant.LEGAL_ENTITY_CREATE_REQUEST);

        LegalEntityDetailResponse response = legalEntityService.create(organizationId, user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                LegalEntityControllerLogConstant.LEGAL_ENTITY_CREATE_COMPLETED);

        return ResponseUtil.created(LegalEntityControllerResponseConstant.RESPONSE_LEGAL_ENTITY_CREATED, response);
    }

    /**
     * Replaces every field of a legal entity of the organization.
     *
     * @param user           the signed-in admin, resolved by {@link GetUser}
     * @param organizationId the organization from the checked header
     * @param legalEntityId  the legal entity identifier
     * @param request        the full new fields
     * @return the stored legal entity
     */
    @PutMapping("/{legalEntityId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update legal entity",
            description = "Replaces every field of a legal entity; an optional field sent as null is cleared. "
                    + "Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Legal entity not found or of another organization "
                    + "(LGE_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "Another legal entity of the organization has this "
                    + "short name, case ignored (LGE_STA_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<LegalEntityDetailResponse>> updateLegalEntity(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID legalEntityId,
            @RequestBody @Valid LegalEntityUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                LegalEntityControllerLogConstant.LEGAL_ENTITY_UPDATE_REQUEST);

        LegalEntityDetailResponse response = legalEntityService.update(organizationId, legalEntityId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                LegalEntityControllerLogConstant.LEGAL_ENTITY_UPDATE_COMPLETED);

        return ResponseUtil.ok(LegalEntityControllerResponseConstant.RESPONSE_LEGAL_ENTITY_UPDATED, response);
    }

    /**
     * Deletes a legal entity of the organization outright.
     *
     * @param user           the signed-in admin, resolved by {@link GetUser}
     * @param organizationId the organization from the checked header
     * @param legalEntityId  the legal entity identifier
     * @return an empty success response
     */
    @DeleteMapping("/{legalEntityId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete legal entity",
            description = "Removes a legal entity of the organization. Organization admins and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Legal entity not found or of another organization "
                    + "(LGE_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "A document or document template names the legal "
                    + "entity (LGE_STA_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteLegalEntity(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID legalEntityId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                LegalEntityControllerLogConstant.LEGAL_ENTITY_DELETE_REQUEST);

        legalEntityService.delete(organizationId, legalEntityId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                LegalEntityControllerLogConstant.LEGAL_ENTITY_DELETE_COMPLETED);

        return ResponseUtil.ok(LegalEntityControllerResponseConstant.RESPONSE_LEGAL_ENTITY_DELETED);
    }
}
