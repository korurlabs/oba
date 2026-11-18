package com.korur.oba.apps.kazan.sprint.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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

import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.sprint.constant.controller.SprintControllerLogConstant;
import com.korur.oba.apps.kazan.sprint.constant.controller.SprintControllerResponseConstant;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintBulkLinkRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintCompleteRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintCreateRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintDeleteRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintSeriesRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintStartRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUnlinkRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUpdateRequest;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintChildBoardResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintDetailResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintListResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintStartPreviewResponse;
import com.korur.oba.apps.kazan.sprint.service.SprintQueryService;
import com.korur.oba.apps.kazan.sprint.service.SprintSeriesService;
import com.korur.oba.apps.kazan.sprint.service.SprintService;
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
 * REST controller for sprint management endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/boards/{boardId}/sprints")
@Tag(name = "Sprints", description = "Sprint management APIs")
public class SprintController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SprintController.class);

    private final SprintService sprintService;
    private final SprintQueryService sprintQueryService;
    private final SprintSeriesService sprintSeriesService;
    private final LoggingService loggingService;

    /**
     * Creates a new sprint for a board.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create sprint",
            description = "Creates a sprint on the board and its child boards. The server derives the sprint's "
                    + "URL slug on each board from its name. Without takeOver the sprint is PLANNED and no active "
                    + "sprint is checked. With takeOver every other ACTIVE sprint linked to one of its boards is "
                    + "completed first: its unfinished tickets on boards linked to the new sprint move into it, "
                    + "the others go to their own board's backlog; then the new sprint starts, also when nothing "
                    + "blocked it. Everything runs in one transaction.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board or of a child "
                    + "board, or, with takeOver, of a board linked to a sprint it would close (KAZ_SEC_002), GUEST "
                    + "role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "The board or a child board not found or of another "
                    + "organization (KAZ_NOT_001), or the caller is not a member of it or, with takeOver, of a board "
                    + "linked to a sprint it would close (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "A concurrent write took the sprint's slug on the "
                    + "board; retrying succeeds (KAZ_STA_017), or with repeat on, the board or a child board "
                    + "belongs to another enabled sprint series (KAZ_STA_018)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001), or a child "
                    + "board is not a descendant of the board (KAZ_VAL_008). With series enabled: a name over 240 "
                    + "characters (KAZ_VAL_060), dates not at 00:00 UTC (KAZ_VAL_040), days before outside 1 to "
                    + "lengthDays - 1 (KAZ_VAL_041)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SprintDetailResponse>> createSprint(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @RequestBody @Valid SprintCreateRequest request) {
        // Log the incoming create request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_CREATE_REQUEST);

        // Delegate creation to the service layer.
        SprintDetailResponse response = sprintService.createSprint(user, organizationId, boardId, request);

        // Log the completed create operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_CREATE_COMPLETED);

        // Return a 201 created response with payload.
        return ResponseUtil.created(SprintControllerResponseConstant.RESPONSE_SPRINT_CREATED, response);
    }

    /**
     * Lists sprints for a board.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List sprints")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), or the caller is not a member of it (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<SprintListResponse>>> listSprints(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId) {
        // Log the incoming list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_LIST_REQUEST);

        // Query sprints for the board.
        List<SprintListResponse> response = sprintQueryService.listSprints(user, organizationId, boardId);

        // Log the completed list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_LIST_COMPLETED);

        // Return a 200 OK response with list payload.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_LIST_RETRIEVED, response);
    }

    /**
     * Lists the descendant boards a new sprint of this board can also be linked to.
     */
    @GetMapping("/child-boards")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List child boards for a new sprint",
            description = "Lists the descendant boards, at every depth, that the caller is a member of, "
                    + "depth-first with each parent before its children. Each entry says whether the caller "
                    + "may link a sprint to it (ADMIN or OWNER) and names its active sprint, if any.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "not a member of the organization, or membership or organization disabled (ORG_SEC_001, "
                    + "ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), or the caller is not a member of it (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<SprintChildBoardResponse>>> listChildBoards(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId) {
        // Log the incoming list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_CHILD_BOARDS_LIST_REQUEST);

        // Query the candidate child boards.
        List<SprintChildBoardResponse> response = sprintQueryService.listChildBoards(user, organizationId, boardId);

        // Log the completed list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_CHILD_BOARDS_LIST_COMPLETED);

        // Return a 200 OK response with list payload.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_CHILD_BOARDS_RETRIEVED, response);
    }

    /**
     * Retrieves sprint details by id.
     */
    @GetMapping("/{sprintId}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get sprint details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), sprint not found (KAZ_NOT_003), or the caller is not a member of the board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SprintDetailResponse>> getSprint(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID sprintId) {
        // Log the incoming detail request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_GET_REQUEST);

        // Query sprint detail.
        SprintDetailResponse response = sprintQueryService.getSprint(user, organizationId, boardId, sprintId);

        // Log the completed detail operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_GET_COMPLETED);

        // Return a 200 OK response with detail payload.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_RETRIEVED, response);
    }

    /**
     * Updates sprint metadata.
     */
    @PutMapping("/{sprintId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update sprint",
            description = "Updates the sprint's metadata. A rename moves the sprint's slug on each board where "
                    + "the slug no longer has the new name's base. A sprint of an enabled series takes no name: "
                    + "seriesName renames the series and every unfinished sprint of it to \"{seriesName} {round}\", "
                    + "with their slugs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), sprint not found (KAZ_NOT_003), or the caller is not a member of the board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "A concurrent write took a new slug of the sprint on "
                    + "one of its boards; retrying succeeds (KAZ_STA_017). Or the sprint changed meanwhile "
                    + "(GEN_STA_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001); a name for a "
                    + "sprint of an enabled series, a seriesName for any other sprint or for a COMPLETED sprint, or a "
                    + "blank or over-240 seriesName (KAZ_VAL_060); new dates of a sprint in an enabled series not at "
                    + "00:00 UTC (KAZ_VAL_040)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SprintDetailResponse>> updateSprint(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID sprintId,
            @RequestBody @Valid SprintUpdateRequest request) {
        // Log the incoming update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_UPDATE_REQUEST);

        // Delegate update to the service layer.
        SprintDetailResponse response = sprintService.updateSprint(user, organizationId, boardId, sprintId, request);

        // Log the completed update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_UPDATE_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_UPDATED, response);
    }

    /**
     * Replaces the "repeat automatically" settings of the sprint's series.
     */
    @PutMapping("/{sprintId}/series")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update sprint series",
            description = "Replaces the settings of the sprint's series; the series is shared by all its sprints "
                    + "and boards. On a sprint outside any series, enabled starts a series named after the sprint "
                    + "with a trailing \" <digits>\" split off as its round (else round 1), and renames the sprint "
                    + "\"{series name} {round}\" when it differs. Switching off keeps the series, its counter and "
                    + "the sprints' names. Switching a series on again takes the series name from this sprint's "
                    + "name and renames the series' unfinished sprints. Dates and days before are checked only "
                    + "when enabled; days before against lengthDays.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), sprint not found (KAZ_NOT_003), or the caller is not a member of the board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "The sprint was completed while the save waited for "
                    + "the series (KAZ_STA_011), a concurrent write took a new slug (KAZ_STA_017), or the sprint "
                    + "changed meanwhile (GEN_STA_001), or switching repeat on would put a linked board into a "
                    + "second enabled sprint series (KAZ_STA_018)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001); dates not at "
                    + "00:00 UTC (KAZ_VAL_040); days before outside 1 to lengthDays - 1 (KAZ_VAL_041); a "
                    + "COMPLETED sprint (KAZ_VAL_042); a series name over 240 characters (KAZ_VAL_060)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SprintDetailResponse>> updateSprintSeries(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID sprintId,
            @RequestBody @Valid SprintSeriesRequest request) {
        // Log the incoming series update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_SERIES_UPDATE_REQUEST);

        // Delegate the series update to the service layer.
        SprintDetailResponse response = sprintSeriesService.updateSeries(
                user, organizationId, boardId, sprintId, request);

        // Log the completed series update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_SERIES_UPDATE_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_SERIES_UPDATED, response);
    }

    /**
     * Previews the active sprints a start of the sprint would close.
     */
    @GetMapping("/{sprintId}/start-preview")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Preview sprint start",
            description = "Lists the boards the caller is a member of that are linked to another ACTIVE sprint "
                    + "sharing a board with this sprint, in this sprint's link order, then boards it is not linked "
                    + "to. Each entry names the sprint a takeover would close, its unfinished ticket count on the "
                    + "board, whether those tickets go to the board's backlog and whether the caller manages the "
                    + "board. canTakeOver is false when the caller is not ADMIN or OWNER of every board linked to "
                    + "a blocking sprint, listed or not. No entries means a plain start works.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "not a member of the organization, or membership or organization disabled (ORG_SEC_001, "
                    + "ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), sprint not found (KAZ_NOT_003), or the caller is not a member of the board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SprintStartPreviewResponse>> getStartPreview(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID sprintId) {
        // Log the incoming preview request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_START_PREVIEW_REQUEST);

        // Query the boards a start would close sprints on.
        SprintStartPreviewResponse response = sprintQueryService.getStartPreview(
                user, organizationId, boardId, sprintId);

        // Log the completed preview operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_START_PREVIEW_COMPLETED);

        // Return a 200 OK response with the preview.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_START_PREVIEW_RETRIEVED, response);
    }

    /**
     * Starts a sprint, optionally taking over the active sprints of its linked boards.
     */
    @PostMapping("/{sprintId}/start")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Start sprint",
            description = "Starts a PLANNED sprint. The body is optional. Without takeOver the sprint starts "
                    + "only when no linked board has another ACTIVE sprint. With takeOver those sprints are "
                    + "completed first: their unfinished tickets on boards linked to this sprint move into it, "
                    + "the others go to their own board's backlog.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sprint started"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board, or, with "
                    + "takeOver, of a board linked to a sprint it would close (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization disabled "
                    + "(ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), sprint not found (KAZ_NOT_003), or the caller is not a member of a board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "Without takeOver, a linked board has another "
                    + "ACTIVE sprint; errorValues.boardNames names them (KAZ_STA_004)"),
            @ApiResponse(responseCode = "422", description = "The sprint is not PLANNED (KAZ_VAL_003)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SprintDetailResponse>> startSprint(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID sprintId,
            @Valid @RequestBody(required = false) SprintStartRequest request) {
        boolean takeOver = request != null && Boolean.TRUE.equals(request.takeOver());

        // Log the incoming start request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_START_REQUEST,
                takeOver);

        // Delegate start operation to the service layer.
        SprintDetailResponse response = sprintService.startSprint(user, organizationId, boardId, sprintId, request);

        // Log the completed start operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_START_COMPLETED);

        // Return a 200 OK response with updated payload.
        String message = takeOver
                ? SprintControllerResponseConstant.RESPONSE_SPRINT_TAKEN_OVER
                : SprintControllerResponseConstant.RESPONSE_SPRINT_STARTED;
        return ResponseUtil.ok(message, response);
    }

    /**
     * Completes a sprint, relocating incomplete tickets per the specified action.
     */
    @PostMapping("/{sprintId}/complete")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Complete sprint",
            description = "Completes the ACTIVE sprint and relocates its unfinished tickets per ticketAction. "
                    + "For a sprint of an enabled series the next round starts right away from today 00:00 UTC: "
                    + "a sprint created ahead is redated and started, else a new round is created for the series "
                    + "length, linked to the series' live boards. When a series board holds another ACTIVE sprint "
                    + "the round stays PLANNED and the series records BLOCKED; the completion still succeeds. "
                    + "moveToNextSprint moves the unfinished tickets into that round on their own boards.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sprint completed; the completed sprint's detail"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board, or of the "
                    + "target board (KAZ_SEC_002), GUEST role in the organization (ORG_SEC_003), not a member of it, "
                    + "or membership or organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board or target board not found or of another "
                    + "organization (KAZ_NOT_001), sprint not found (KAZ_NOT_003), or the caller is not a member of "
                    + "the board (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001); the sprint is "
                    + "not ACTIVE, also when the recurrence job completed it meanwhile (KAZ_VAL_004); "
                    + "moveToNextSprint without MOVE_TO_SPRINT, with a target sprint or board, or for a sprint "
                    + "outside an enabled series with a live board (KAZ_VAL_061)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SprintDetailResponse>> completeSprint(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID sprintId,
            @RequestBody @Valid SprintCompleteRequest request) {
        // Log the incoming complete request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_COMPLETE_REQUEST);

        // Delegate complete operation to the service layer.
        SprintDetailResponse response = sprintService.completeSprint(user, organizationId, boardId, sprintId, request);

        // Log the completed complete operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_COMPLETE_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_COMPLETED, response);
    }

    /**
     * Links the sprint to another board.
     */
    @PostMapping("/{sprintId}/link")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Link sprint",
            description = "Links the sprint to the board of the path, restoring an earlier link when one "
                    + "exists. The link gets a slug free on that board.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), sprint not found (KAZ_NOT_003), or the caller is not a member of the board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "A concurrent write took the sprint's slug on the "
                    + "board; retrying succeeds (KAZ_STA_017), or the board belongs to another enabled sprint "
                    + "series than the sprint's (KAZ_STA_018)"),
            @ApiResponse(responseCode = "422", description = "Sprint already linked to the board (KAZ_VAL_007), "
                    + "or the board is not a parent, child or sibling of the sprint's boards (KAZ_VAL_008)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SprintDetailResponse>> linkSprint(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID sprintId) {
        // Log the incoming link request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_LINK_REQUEST);

        // Delegate link operation to the service layer.
        SprintDetailResponse response = sprintService.linkSprint(user, organizationId, boardId, sprintId);

        // Log the completed link operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_LINK_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_LINKED, response);
    }

    /**
     * Bulk-links the sprint to multiple boards in a single operation.
     */
    @PostMapping("/{sprintId}/bulk-link")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Bulk link sprint to boards",
            description = "Links the sprint to each given board that is not linked yet, restoring earlier "
                    + "links where they exist. Each link gets a slug free on its board.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board or of a target "
                    + "board (KAZ_SEC_002), GUEST role in the organization (ORG_SEC_003), not a member of it, or "
                    + "membership or organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "A board not found or of another organization "
                    + "(KAZ_NOT_001), the sprint not found (KAZ_NOT_003), or the caller is not a member of a board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "A concurrent write took the sprint's slug on one of "
                    + "the boards; retrying succeeds (KAZ_STA_017), or a board belongs to another enabled "
                    + "sprint series than the sprint's (KAZ_STA_018)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001), or a target "
                    + "board is not a parent, child or sibling of the sprint's boards (KAZ_VAL_008)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SprintDetailResponse>> bulkLinkSprint(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID sprintId,
            @RequestBody @Valid SprintBulkLinkRequest request) {
        // Log the incoming bulk link request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_BULK_LINK_REQUEST);

        // Delegate bulk link operation to the service layer.
        SprintDetailResponse response = sprintService.bulkLinkSprint(user, organizationId, boardId, sprintId, request);

        // Log the completed bulk link operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_BULK_LINK_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_BULK_LINKED, response);
    }

    /**
     * Unlinks the sprint.
     */
    @PostMapping("/{sprintId}/unlink")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Unlink sprint")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board or of the "
                    + "transfer board (KAZ_SEC_002), GUEST role in the organization (ORG_SEC_003), not a member of it, "
                    + "or membership or organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board or transfer board not found or of another "
                    + "organization (KAZ_NOT_001), sprint not found (KAZ_NOT_003), or the caller is not a member of a "
                    + "board (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> unlinkSprint(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID sprintId,
            @RequestBody @Valid SprintUnlinkRequest request) {
        // Log the incoming unlink request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_UNLINK_REQUEST);

        // Delegate unlink operation to the service layer.
        sprintService.unlinkSprint(user, organizationId, boardId, sprintId, request);

        // Log the completed unlink operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_UNLINK_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_UNLINKED);
    }

    /**
     * Deletes a sprint, handling its tickets per the specified action.
     */
    @DeleteMapping("/{sprintId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete sprint")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), sprint not found (KAZ_NOT_003), or the caller is not a member of the board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteSprint(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID sprintId,
            @ModelAttribute SprintDeleteRequest request) {
        // Log the incoming delete request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_DELETE_REQUEST);

        // Delegate delete operation to the service layer.
        sprintService.deleteSprint(user, organizationId, boardId, sprintId, request);

        // Log the completed delete operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_DELETE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_DELETED);
    }

    /**
     * Restores a soft-deleted sprint.
     */
    @PostMapping("/{sprintId}/restore")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Restore sprint")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), sprint not found (KAZ_NOT_003), or the caller is not a member of the board "
                    + "(KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> restoreSprint(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID sprintId) {
        // Log the incoming restore request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_RESTORE_REQUEST);

        // Delegate restore operation to the service layer.
        sprintService.restoreSprint(user, organizationId, boardId, sprintId);

        // Log the completed restore operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                SprintControllerLogConstant.SPRINT_RESTORE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(SprintControllerResponseConstant.RESPONSE_SPRINT_RESTORED);
    }

}
