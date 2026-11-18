package com.korur.oba.apps.kaya.plan.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.plan.constant.controller.PlanControllerLogConstant;
import com.korur.oba.apps.kaya.plan.constant.controller.PlanControllerResponseConstant;
import com.korur.oba.apps.kaya.plan.dto.request.PlanFeedbackAckRequest;
import com.korur.oba.apps.kaya.plan.dto.request.PlanFeedbackDeliveryQueryRequest;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFeedbackAckResponse;
import com.korur.oba.apps.kaya.plan.dto.response.PlanFeedbackDeliveryResponse;
import com.korur.oba.apps.kaya.plan.mapper.PlanMapper;
import com.korur.oba.apps.kaya.plan.service.PlanQueryService;
import com.korur.oba.apps.kaya.plan.service.PlanService;

/**
 * Plan feedback delivery to the CLI, on the machine token chain: the bearer machine token names the
 * organization and the user, so the request carries no JWT and no X-Organization-ID, and only
 * the feedback on plans of that user is served or acknowledged. The CLI injects the feedback into
 * the user's sessions as hook context and acknowledges it, which is what the plan page shows as
 * delivered.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/delivery/plan-feedback")
@Tag(name = "Plan feedback delivery", description = "Review feedback on plans for the CLI, authenticated by a machine token")
public class PlanDeliveryController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PlanDeliveryController.class);

    private final PlanQueryService planQueryService;
    private final PlanService planService;
    private final PlanMapper planMapper;
    private final LoggingService loggingService;

    @GetMapping
    @Operation(summary = "Get the pending feedback of the token's user",
            description = "Bearer machine token. Either sessionId (repeated, 1 to 20): the feedback no session has received "
                    + "on plans whose latest version came from these sessions of the token's user; or remoteUrl and "
                    + "branch: the same for plans of the checkout's repository whose latest session of that user has "
                    + "ended on that branch. Oldest first, at most 50. An unknown or unreadable remote, unknown sessions "
                    + "and plans of another organization or user give an empty list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pending feedback, possibly empty"),
            @ApiResponse(responseCode = "400", description = "KAY_PLN_004: neither form or both given"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token missing, unknown, revoked or expired, "
                    + "or machine retired"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002 or KAY_SEC_002: the user no longer acts in "
                    + "the organization; APP_SEC_001: the user has no Kaya access"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: more than 20 session ids, a session id not in "
                    + "the session id pattern, remoteUrl over 500 or branch over 255 characters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<PlanFeedbackDeliveryResponse>>> getPending(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @Valid @ModelAttribute @ParameterObject PlanFeedbackDeliveryQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.PENDING_REQUEST, principal.machineId());
        List<PlanFeedbackDeliveryResponse> response = planQueryService.getPendingFeedback(principal, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.PENDING_COMPLETED,
                response.size(), principal.machineId());
        return ResponseUtil.ok(PlanControllerResponseConstant.RESPONSE_PENDING_RETRIEVED, response);
    }

    @PostMapping("/acks")
    @Operation(summary = "Acknowledge delivered feedback",
            description = "Bearer machine token. Marks the feedback delivered to the named session and lowers each plan's "
                    + "open feedback count by the rows marked. Rows already delivered, and ids outside the token's "
                    + "organization and user, are skipped, so a resend changes nothing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recorded; acknowledged counts the rows marked now"),
            @ApiResponse(responseCode = "400", description = "The body malformed"),
            @ApiResponse(responseCode = "401", description = "KAY_SEC_003: token refused"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_002 or KAY_SEC_002: the user no longer acts in "
                    + "the organization; APP_SEC_001: the user has no Kaya access"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: sessionId missing or not a session id, no "
                    + "feedback id, more than 50, or a null one"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<PlanFeedbackAckResponse>> acknowledge(
            @Parameter(hidden = true) @AuthenticationPrincipal MachinePrincipal principal,
            @Valid @RequestBody PlanFeedbackAckRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.ACKS_REQUEST,
                principal.machineId(), request.feedbackIds().size(), request.sessionId());
        int acknowledged = planService.acknowledge(principal, request.sessionId(), request.feedbackIds());
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, PlanControllerLogConstant.ACKS_COMPLETED,
                acknowledged, request.sessionId(), principal.machineId());
        return ResponseUtil.ok(PlanControllerResponseConstant.RESPONSE_FEEDBACK_ACKNOWLEDGED,
                planMapper.toAckResponse(request.sessionId(), acknowledged));
    }
}
