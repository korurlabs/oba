package com.korur.oba.apps.kaizen.ticket.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.milestone.dto.response.HomeDeadlineResponse;
import com.korur.oba.apps.kaizen.milestone.service.MilestoneQueryService;
import com.korur.oba.apps.kaizen.ticket.constant.controller.TicketControllerLogConstant;
import com.korur.oba.apps.kaizen.ticket.constant.controller.TicketControllerResponseConstant;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeLeaderboardResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeSprintResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeStaleBacklogResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeSummaryResponse;
import com.korur.oba.apps.kaizen.ticket.enums.HomeLeaderboardPeriod;
import com.korur.oba.apps.kaizen.ticket.service.TicketQueryService;
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
 * REST controller for the home page, which summarises the open work across all boards
 * where the authenticated user is a member. Every endpoint is open to any authenticated
 * user and reads only the boards that user is a member of, so a user without boards gets
 * empty data rather than an error.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaizen/home")
@Tag(name = "Home", description = "Home page summary APIs")
public class HomeSummaryController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(HomeSummaryController.class);

    private final TicketQueryService ticketQueryService;
    private final MilestoneQueryService milestoneQueryService;
    private final LoggingService loggingService;

    /**
     * Returns the open incident count and the open active-sprint ticket count across the
     * boards where the authenticated user is a member.
     */
    @GetMapping("/summary")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get the open work summary across the user's boards")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<HomeSummaryResponse>> getHomeSummary(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        // Log the incoming home summary request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.HOME_SUMMARY_REQUEST);

        // Delegate to the query service.
        HomeSummaryResponse response = ticketQueryService.getHomeSummary(user.getId(), organizationId);

        // Log the completed home summary operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.HOME_SUMMARY_COMPLETED);

        // Return a 200 OK response with the summary.
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_HOME_SUMMARY_RETRIEVED, response);
    }

    /**
     * Returns the stale backlog tickets across the boards where the authenticated user is
     * a member, with the total and whether the user may delete each one.
     */
    @GetMapping("/stale-backlog")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get the stale backlog tickets across the user's boards")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<HomeStaleBacklogResponse>> getStaleBacklog(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, TicketControllerLogConstant.HOME_STALE_BACKLOG_REQUEST);

        HomeStaleBacklogResponse response = ticketQueryService.getStaleBacklog(user.getId(), organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, TicketControllerLogConstant.HOME_STALE_BACKLOG_COMPLETED);
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_HOME_STALE_BACKLOG_RETRIEVED, response);
    }

    /**
     * Returns the size-weighted leaderboard of finished tickets across the boards where
     * the authenticated user is a member.
     */
    @GetMapping("/leaderboard")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get the size-weighted leaderboard across the user's boards")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<HomeLeaderboardResponse>> getLeaderboard(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Parameter(description = "SPRINT (default) or LAST_30_DAYS; SPRINT without an active sprint "
                    + "answers LAST_30_DAYS")
            @RequestParam(defaultValue = "SPRINT") HomeLeaderboardPeriod period) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, TicketControllerLogConstant.HOME_LEADERBOARD_REQUEST);

        HomeLeaderboardResponse response = ticketQueryService.getLeaderboard(user.getId(), organizationId, period);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, TicketControllerLogConstant.HOME_LEADERBOARD_COMPLETED);
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_HOME_LEADERBOARD_RETRIEVED, response);
    }

    /**
     * Returns the ACTIVE sprint of the focus board with its burndown, and the user's
     * boards with an ACTIVE sprint to choose another focus board from.
     */
    @GetMapping("/sprint")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get the focus board's active sprint and burndown")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<HomeSprintResponse>> getHomeSprint(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Parameter(description = "Requested focus board; ignored when it is not one of the user's boards "
                    + "with an active sprint")
            @RequestParam(required = false) UUID boardId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, TicketControllerLogConstant.HOME_SPRINT_REQUEST);

        HomeSprintResponse response = ticketQueryService.getHomeSprint(user.getId(), organizationId, boardId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, TicketControllerLogConstant.HOME_SPRINT_COMPLETED);
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_HOME_SPRINT_RETRIEVED, response);
    }

    /**
     * Returns the overdue and upcoming OPEN milestones across the boards where the
     * authenticated user is a member.
     */
    @GetMapping("/deadlines")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get the upcoming milestone deadlines across the user's boards")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<HomeDeadlineResponse>>> getHomeDeadlines(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, TicketControllerLogConstant.HOME_DEADLINES_REQUEST);

        List<HomeDeadlineResponse> response = milestoneQueryService.listHomeDeadlines(user.getId(), organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, TicketControllerLogConstant.HOME_DEADLINES_COMPLETED);
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_HOME_DEADLINES_RETRIEVED, response);
    }
}
