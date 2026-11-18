package com.korur.oba.apps.kazan.milestone.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.korur.oba.apps.kazan.milestone.constant.controller.MilestoneControllerLogConstant;
import com.korur.oba.apps.kazan.milestone.constant.controller.MilestoneControllerResponseConstant;
import com.korur.oba.apps.kazan.milestone.dto.response.MilestoneDetailResponse;
import com.korur.oba.apps.kazan.milestone.service.MilestoneService;
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
 * REST controller for milestone dependencies: "milestone depends on dependsOn".
 *
 * Both endpoints need ADMIN on the dependent milestone's board and membership
 * on the board of the milestone it depends on. A dependency only warns about
 * dates; it never shifts them.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/boards/{boardId}/milestones/{milestoneId}/dependencies")
@Tag(name = "Milestones", description = "Milestone management APIs")
public class MilestoneDependencyController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MilestoneDependencyController.class);

    private final MilestoneService milestoneService;
    private final LoggingService loggingService;

    /**
     * Makes the milestone depend on another one; an existing pair is left as is.
     */
    @PostMapping("/{dependsOnId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Add milestone dependency")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Self dependency, unrelated boards or cycle, or missing "
                    + "or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Insufficient board permissions, GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization disabled "
                    + "(ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or milestone "
                    + "not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MilestoneDetailResponse>> addDependency(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID milestoneId,
            @PathVariable UUID dependsOnId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_DEPENDENCY_ADD_REQUEST);

        MilestoneDetailResponse response = milestoneService.addDependency(
                user, organizationId, boardId, milestoneId, dependsOnId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_DEPENDENCY_ADD_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_DEPENDENCY_ADDED, response);
    }

    /**
     * Removes the dependency of the milestone on another one.
     */
    @DeleteMapping("/{dependsOnId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Remove milestone dependency")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Insufficient board permissions, GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization disabled "
                    + "(ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or milestone "
                    + "or dependency not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MilestoneDetailResponse>> removeDependency(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID milestoneId,
            @PathVariable UUID dependsOnId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_DEPENDENCY_REMOVE_REQUEST);

        MilestoneDetailResponse response = milestoneService.removeDependency(
                user, organizationId, boardId, milestoneId, dependsOnId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MilestoneControllerLogConstant.MILESTONE_DEPENDENCY_REMOVE_COMPLETED);

        return ResponseUtil.ok(MilestoneControllerResponseConstant.RESPONSE_MILESTONE_DEPENDENCY_REMOVED, response);
    }
}
