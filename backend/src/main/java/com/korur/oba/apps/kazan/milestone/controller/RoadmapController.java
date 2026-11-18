package com.korur.oba.apps.kazan.milestone.controller;

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
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.constant.controller.MilestoneControllerLogConstant;
import com.korur.oba.apps.kazan.milestone.constant.controller.MilestoneControllerResponseConstant;
import com.korur.oba.apps.kazan.milestone.dto.request.RoadmapQueryRequest;
import com.korur.oba.apps.kazan.milestone.dto.response.RoadmapResponse;
import com.korur.oba.apps.kazan.milestone.service.MilestoneQueryService;
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
 * REST controller for the board roadmap, a read-only view over milestones.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/boards/{boardId}/roadmap")
@Tag(name = "Roadmap", description = "Board roadmap APIs")
public class RoadmapController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RoadmapController.class);

    private final MilestoneQueryService milestoneQueryService;
    private final LoggingService loggingService;

    /**
     * Returns the roadmap of a board, optionally with one lane per child board.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get board roadmap")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Window start after window end, or missing or malformed "
                    + "X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "membership not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<RoadmapResponse>> getRoadmap(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @Valid @ModelAttribute @ParameterObject RoadmapQueryRequest query) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.ROADMAP_GET_REQUEST);

        RoadmapResponse response = milestoneQueryService.getRoadmap(
                user, organizationId, boardId, query.isIncludeChildBoards(), query.getFrom(), query.getTo());

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.ROADMAP_GET_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_ROADMAP_RETRIEVED, response);
    }
}
