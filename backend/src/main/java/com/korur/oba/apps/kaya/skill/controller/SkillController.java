package com.korur.oba.apps.kaya.skill.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.skill.constant.controller.SkillControllerLogConstant;
import com.korur.oba.apps.kaya.skill.constant.controller.SkillControllerResponseConstant;
import com.korur.oba.apps.kaya.skill.dto.request.SkillQueryRequest;
import com.korur.oba.apps.kaya.skill.dto.response.SkillDetailResponse;
import com.korur.oba.apps.kaya.skill.dto.response.SkillListResponse;
import com.korur.oba.apps.kaya.skill.service.SkillQueryService;

/**
 * The skills of the versions of the organization named by the X-Organization-ID header, read by every
 * member through {@link SkillQueryService}; skills are written only by parsing a version's files.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/skills")
@Tag(name = "Skills", description = "Skills of pack and project versions and the parts they link")
public class SkillController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SkillController.class);

    private final SkillQueryService skillQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the skills of the given versions.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the skills of versions",
            description = "Not paged; ordered by version, name and id. A version id of another organization matches nothing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a version id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: no version id or more than 20"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<SkillListResponse>>> getSkills(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject SkillQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SkillControllerLogConstant.SKILL_LIST_REQUEST,
                request.getVersionId().size(), organizationId);
        List<SkillListResponse> response = skillQueryService.getSkills(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SkillControllerLogConstant.SKILL_LIST_COMPLETED,
                response.size(), organizationId);
        return ResponseUtil.ok(SkillControllerResponseConstant.RESPONSE_SKILLS_RETRIEVED, response);
    }

    /**
     * Reads one skill with its SKILL.md and its parts.
     */
    @GetMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a skill", description = "The index fields, the whole SKILL.md as UTF-8 text, "
            + "and the linked parts ordered by path with the number of skills sharing each")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID or the id not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the organization has no such skill, or its blob is missing"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SkillDetailResponse>> getSkill(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SkillControllerLogConstant.SKILL_GET_REQUEST, id, organizationId);
        SkillDetailResponse response = skillQueryService.getSkill(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SkillControllerLogConstant.SKILL_GET_COMPLETED, id, organizationId);
        return ResponseUtil.ok(SkillControllerResponseConstant.RESPONSE_SKILL_RETRIEVED, response);
    }
}
