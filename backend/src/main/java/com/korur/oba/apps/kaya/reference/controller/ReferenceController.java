package com.korur.oba.apps.kaya.reference.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

// 3. Java SDK
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
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.reference.constant.controller.ReferenceControllerLogConstant;
import com.korur.oba.apps.kaya.reference.constant.controller.ReferenceControllerResponseConstant;
import com.korur.oba.apps.kaya.reference.dto.response.ReferenceDetailResponse;
import com.korur.oba.apps.kaya.reference.service.ReferenceQueryService;

/**
 * The references of the versions of the organization named by the X-Organization-ID header, read by every
 * member through {@link ReferenceQueryService}; references are listed only as parts of a skill and written only by
 * parsing a version's files.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/references")
@Tag(name = "References", description = "Documentation and asset files of the skills of pack and project versions")
public class ReferenceController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ReferenceController.class);

    private final ReferenceQueryService referenceQueryService;
    private final LoggingService loggingService;

    /**
     * Reads one reference with its content.
     */
    @GetMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a reference", description = "The index fields and the whole file, as UTF-8 text in content or, when it is not valid UTF-8, as base64 in contentBase64")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID or the id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no such reference"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ReferenceDetailResponse>> getReference(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReferenceControllerLogConstant.REFERENCE_GET_REQUEST, id, organizationId);
        ReferenceDetailResponse response = referenceQueryService.getReference(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ReferenceControllerLogConstant.REFERENCE_GET_COMPLETED, id, organizationId);
        return ResponseUtil.ok(ReferenceControllerResponseConstant.RESPONSE_REFERENCE_RETRIEVED, response);
    }
}
