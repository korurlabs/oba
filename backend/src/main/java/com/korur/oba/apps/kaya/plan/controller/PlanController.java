package com.korur.oba.apps.kaya.plan.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import jakarta.validation.constraints.Min;
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
import com.korur.oba.apps.kaya.plan.constant.PlanConstant;
import com.korur.oba.apps.kaya.plan.constant.controller.PlanControllerLogConstant;
import com.korur.oba.apps.kaya.plan.constant.controller.PlanControllerResponseConstant;
import com.korur.oba.apps.kaya.plan.dto.request.PlanFeedbackRequest;
import com.korur.oba.apps.kaya.plan.dto.request.PlanQueryRequest;
import com.korur.oba.apps.kaya.plan.dto.response.PlanDetailResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFeedbackResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFiltersResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanListResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanVersionContentResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanVersionDiffResponse;
import com.korur.oba.apps.kaya.plan.service.PlanQueryService;
import com.korur.oba.apps.kaya.plan.service.PlanService;

/**
 * Plans of the organization named by the X-Organization-ID header: the list, one plan with its
 * versions and diffs, and the feedback members give. Every member reads, comments and requests
 * changes; approving is open to "the plan's user, ADMIN or OWNER" and withdrawing to the
 * feedback's reviewer, so both take MEMBER here and the service checks the rest.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/plans")
@Tag(name = "Plans", description = "Plans Claude Code wrote and the feedback members give on them")
public class PlanController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PlanController.class);

    private final PlanService planService;
    private final PlanQueryService planQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the plans of the organization.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the plans of the organization",
            description = "Paged, 25 by default and 100 at most; newest change first. search matches the title or the "
                    + "path; sessionId lists the plans the session wrote a version of")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: sessionId names no session of the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a paging or filter value is invalid"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<PlanListResponse>>> getPlans(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject PlanQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.PLAN_LIST_REQUEST, organizationId);
        Page<PlanListResponse> response = planQueryService.listPlans(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.PLAN_LIST_COMPLETED,
                response.getNumberOfElements(), organizationId);
        return ResponseUtil.ok(PlanControllerResponseConstant.RESPONSE_PLANS_RETRIEVED, response);
    }

    /**
     * The options of the list's filters.
     */
    @GetMapping("/filters")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read the plan filter options",
            description = "The repositories, packs and users the organization's plans name, each by name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PlanFiltersResponse>> getFilters(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.FILTERS_REQUEST, organizationId);
        PlanFiltersResponse response = planQueryService.getFilters(organizationId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.FILTERS_COMPLETED, organizationId);
        return ResponseUtil.ok(PlanControllerResponseConstant.RESPONSE_FILTERS_RETRIEVED, response);
    }

    /**
     * Reads one plan.
     */
    @GetMapping("/{planId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a plan",
            description = "With its latest session, versions, feedback, the handoffs of its feature, its planning handoff "
                    + "and whether the signed-in user may approve it")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or the plan id is not "
                    + "a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such plan in the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PlanDetailResponse>> getPlan(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID planId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.PLAN_GET_REQUEST,
                user.getId(), planId, organizationId);
        PlanDetailResponse response = planQueryService.getPlan(user, organizationId, planId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.PLAN_GET_COMPLETED,
                planId, response.versions().size(), response.feedback().size(), organizationId);
        return ResponseUtil.ok(PlanControllerResponseConstant.RESPONSE_PLAN_RETRIEVED, response);
    }

    /**
     * Reads the content of one version of a plan.
     */
    @GetMapping("/{planId}/versions/{number}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a plan version", description = "The content as captured")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, the plan id is not a "
                    + "UUID, or the version number is not an integer"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such plan in the organization, or no such version"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: the version number is below 1"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PlanVersionContentResponse>> getVersion(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID planId,
            @PathVariable @Min(value = 1, message = PlanConstant.VERSION_NUMBER_MIN_MESSAGE) int number) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.VERSION_GET_REQUEST,
                number, planId, organizationId);
        PlanVersionContentResponse response = planQueryService.getVersion(organizationId, planId, number);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.VERSION_GET_COMPLETED,
                number, planId, organizationId);
        return ResponseUtil.ok(PlanControllerResponseConstant.RESPONSE_VERSION_RETRIEVED, response);
    }

    /**
     * Reads the diff of one version of a plan against the version before it.
     */
    @GetMapping("/{planId}/versions/{number}/diff")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read the changes of a plan version",
            description = "The unified diff against version number - 1; empty for version 1")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, the plan id is not a "
                    + "UUID, or the version number is not an integer"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such plan in the organization, or no such version"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: the version number is below 1"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PlanVersionDiffResponse>> getDiff(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID planId,
            @PathVariable @Min(value = 1, message = PlanConstant.VERSION_NUMBER_MIN_MESSAGE) int number) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.DIFF_GET_REQUEST,
                number, planId, organizationId);
        PlanVersionDiffResponse response = planQueryService.getDiff(organizationId, planId, number);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.DIFF_GET_COMPLETED,
                number, planId, organizationId);
        return ResponseUtil.ok(PlanControllerResponseConstant.RESPONSE_DIFF_RETRIEVED, response);
    }

    /**
     * Gives feedback on a version of a plan.
     */
    @PostMapping("/{planId}/feedback")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Give feedback on a plan",
            description = "APPROVE sets the plan APPROVED and is open to the plan's user, ADMIN and OWNER; "
                    + "REQUEST_CHANGES sets it CHANGES_REQUESTED; COMMENT leaves the state. A decision needs the latest "
                    + "version, a comment may be on any. The feedback waits until the plan's session receives it")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Feedback recorded"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, the plan id is not a "
                    + "UUID, or a malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization; GEN_SEC_003: "
                    + "APPROVE by a member who is neither the plan's user nor ADMIN or OWNER"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such plan in the organization, or no such version"),
            @ApiResponse(responseCode = "409", description = "KAY_PLN_001: APPROVE or REQUEST_CHANGES on a version that is not "
                    + "the latest"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a field is invalid; KAY_PLN_002: no comment on "
                    + "REQUEST_CHANGES or COMMENT, one line bound only, lineEnd before lineStart, or a range past the "
                    + "version's last line"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PlanFeedbackResponse>> giveFeedback(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID planId,
            @Valid @RequestBody PlanFeedbackRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.FEEDBACK_CREATE_REQUEST,
                user.getId(), request.decision(), planId, request.versionNumber(), organizationId);
        PlanFeedbackResponse response = planService.giveFeedback(user, organizationId, planId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.FEEDBACK_CREATE_COMPLETED,
                response.id(), planId, organizationId);
        return ResponseUtil.created(PlanControllerResponseConstant.RESPONSE_FEEDBACK_CREATED, response);
    }

    /**
     * Withdraws the signed-in member's own feedback while it waits for delivery.
     */
    @DeleteMapping("/{planId}/feedback/{feedbackId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Withdraw feedback on a plan",
            description = "Only the reviewer, and only before a session received it; a withdrawn decision gives the "
                    + "state back to the decisions left on the latest version")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or the plan or "
                    + "feedback id is not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization; GEN_SEC_003: "
                    + "another member's feedback"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such plan in the organization, or no such feedback"),
            @ApiResponse(responseCode = "409", description = "KAY_PLN_003: the feedback has been delivered"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> withdrawFeedback(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID planId,
            @PathVariable UUID feedbackId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.FEEDBACK_DELETE_REQUEST,
                user.getId(), feedbackId, planId, organizationId);
        planService.withdrawFeedback(user, organizationId, planId, feedbackId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.FEEDBACK_DELETE_COMPLETED,
                feedbackId, planId, organizationId);
        return ResponseUtil.ok(PlanControllerResponseConstant.RESPONSE_FEEDBACK_WITHDRAWN);
    }
}
