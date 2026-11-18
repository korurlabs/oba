package com.korur.oba.apps.kaizen.dailyupdate.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.constant.controller.DailyUpdateControllerLogConstant;
import com.korur.oba.apps.kaizen.dailyupdate.constant.controller.DailyUpdateControllerResponseConstant;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateCreateRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateMissingRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdatePreviewRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateQueryRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateUpdateRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateMissingResponse;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateNudgeResponse;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdatePreviewResponse;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateNudgeService;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateService;
import com.korur.oba.apps.kaizen.ticket.dto.response.AllTicketListResponse;
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.entity.User;

/**
 * REST controller for daily updates.
 *
 * Any logged-in user may call it; no organization header is needed. Create
 * reads the optional X-Organization-ID header to pick the chat channel the
 * update is posted to, and needs the MEMBER role in that organization, or
 * ADMIN or OWNER: a GUEST cannot write in its chat. A user reads their own updates and those of colleagues
 * who share an ACTIVE organization with them, and every line is shown to
 * every such reader; a line's ticket opens only for an active member of the
 * update's organization who is a member of its board. Only
 * the author may edit or delete an update. Tickets on a line must be on a
 * board of the update's organization the author is a member of when the line
 * is added. The list of members
 * who have not posted is for an organization's OWNERs and ADMINs only, and
 * only they may nudge one of them, once per recipient, organization and
 * Istanbul day.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaizen/updates")
@Tag(name = "Daily Updates", description = "Daily update APIs")
public class DailyUpdateController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DailyUpdateController.class);

    private final DailyUpdateService dailyUpdateService;
    private final DailyUpdateQueryService dailyUpdateQueryService;
    private final DailyUpdateNudgeService dailyUpdateNudgeService;
    private final LoggingService loggingService;

    /**
     * Lists the tickets to preselect in a new update.
     */
    @GetMapping("/suggested-tickets")
    @Operation(summary = "List suggested tickets",
            description = "Tickets assigned to the caller, IN_PROGRESS, on the caller's boards of the X-Organization-ID "
                    + "organization, else of the caller's default ACTIVE organization, sub-tickets included, ordered "
                    + "by key, at most 30; empty without any ACTIVE organization")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<AllTicketListResponse>>> listSuggestedTickets(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(value = CommonConstant.HEADER_ORGANIZATION_ID, required = false) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_SUGGESTED_TICKETS_REQUEST);

        List<AllTicketListResponse> response = dailyUpdateQueryService.listSuggestedTickets(user, organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_SUGGESTED_TICKETS_COMPLETED);

        return ResponseUtil.ok(DailyUpdateControllerResponseConstant.RESPONSE_DAILY_UPDATE_SUGGESTED_TICKETS_RETRIEVED,
                response);
    }

    /**
     * Lists the members of an organization who have not posted an update by
     * the day's deadline, 10:30 Europe/Istanbul.
     */
    @GetMapping("/missing")
    @Operation(summary = "List members without a daily update",
            description = "Expected members (ACTIVE, role OWNER, ADMIN or MEMBER, joined before the deadline) without an "
                    + "update before 10:30 Europe/Istanbul on the given day (default today there): first those who have "
                    + "not posted, then those who posted late with the time of their first update. Members without "
                    + "Kaizen access in the organization are not listed or counted. Empty on a weekend. Only the "
                    + "organization's OWNERs and ADMINs may call it")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Caller is not OWNER or ADMIN, membership disabled, or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Organization not found or caller is not a member"),
            @ApiResponse(responseCode = "422", description = "Missing organizationId or date in the future"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DailyUpdateMissingResponse>> getMissingDailyUpdates(
            @Parameter(hidden = true) @GetUser User user,
            @Valid @ModelAttribute @ParameterObject DailyUpdateMissingRequest query) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_MISSING_REQUEST);

        DailyUpdateMissingResponse response = dailyUpdateQueryService.getMissingDailyUpdates(user, query);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_MISSING_COMPLETED);

        return ResponseUtil.ok(DailyUpdateControllerResponseConstant.RESPONSE_DAILY_UPDATE_MISSING_RETRIEVED,
                response);
    }

    /**
     * Reminds a member who has not posted today's update, with one push.
     */
    @PostMapping("/missing/{userId}/nudge")
    @Operation(summary = "Nudge a member without a daily update",
            description = "Sends one push to the member reminding them to post today's update (Europe/Istanbul day, "
                    + "deadline 10:30). At most one nudge per member, organization and day, whoever sends it. Only an "
                    + "OWNER or ADMIN of the X-Organization-ID organization may nudge; a MEMBER or GUEST may not. The "
                    + "push is sent after the nudge is saved; a failed push does not undo the nudge")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is a MEMBER or GUEST (ORG_SEC_003), the caller's "
                    + "membership is disabled (ORG_SEC_002), the organization is disabled (ORG_SEC_005), or the "
                    + "caller has no Kaizen access in the organization (APP_SEC_001)"),
            @ApiResponse(responseCode = "404", description = "Organization not found (ORG_NOT_001), caller is not a "
                    + "member (ORG_NOT_002), or the user is not an ACTIVE OWNER, ADMIN or MEMBER with an ACTIVE "
                    + "account and Kaizen access in the organization (DLY_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "Member already nudged today (DLY_STA_001)"),
            @ApiResponse(responseCode = "422", description = "Nudging oneself (DLY_VAL_001), or the member already "
                    + "posted today (DLY_VAL_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DailyUpdateNudgeResponse>> nudgeMissingMember(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID userId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_NUDGE_REQUEST);

        DailyUpdateNudgeResponse response = dailyUpdateNudgeService.nudge(user, organizationId, userId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_NUDGE_COMPLETED);

        return ResponseUtil.created(DailyUpdateControllerResponseConstant.RESPONSE_DAILY_UPDATE_NUDGE_SENT, response);
    }

    /**
     * Previews a new update without saving it.
     */
    @PostMapping("/preview")
    @Operation(summary = "Preview daily update",
            description = "Returns the lines and summary text a create would produce; progressSynced tells which lines would set ticket progress. "
                    + "Resolves and checks the organization as a create does. Saves nothing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not an ACTIVE MEMBER, ADMIN or OWNER of the organization the update would be posted in, or that organization is disabled"),
            @ApiResponse(responseCode = "404", description = "Ticket not found or not on a board of the caller in that organization"),
            @ApiResponse(responseCode = "422", description = "Invalid request or duplicate ticket"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DailyUpdatePreviewResponse>> previewDailyUpdate(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(value = CommonConstant.HEADER_ORGANIZATION_ID, required = false) UUID organizationId,
            @RequestBody @Valid DailyUpdatePreviewRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_PREVIEW_REQUEST);

        DailyUpdatePreviewResponse response = dailyUpdateQueryService.previewDailyUpdate(user, organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_PREVIEW_COMPLETED);

        return ResponseUtil.ok(DailyUpdateControllerResponseConstant.RESPONSE_DAILY_UPDATE_PREVIEWED, response);
    }

    /**
     * Creates an update, writes eligible percentages to their tickets and
     * posts the update to the chat channel of the request's organization.
     */
    @PostMapping
    @Operation(summary = "Create daily update",
            description = "Also posts the update to the chat channel of the X-Organization-ID organization; without "
                    + "the header, of the caller's default ACTIVE organization, else of the first ACTIVE one by join "
                    + "date; without any, the update is saved without a channel entry. Posting needs the role "
                    + "MEMBER, ADMIN or OWNER in that organization; a GUEST is refused")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not an ACTIVE MEMBER, ADMIN or OWNER of the organization the update is posted in, or that organization is disabled"),
            @ApiResponse(responseCode = "404", description = "Ticket not found or not on a board of the caller in that organization"),
            @ApiResponse(responseCode = "422", description = "Invalid request or duplicate ticket"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DailyUpdateResponse>> createDailyUpdate(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(value = CommonConstant.HEADER_ORGANIZATION_ID, required = false) UUID organizationId,
            @RequestBody @Valid DailyUpdateCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_CREATE_REQUEST);

        DailyUpdateResponse response = dailyUpdateService.createDailyUpdate(user, organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_CREATE_COMPLETED);

        return ResponseUtil.created(DailyUpdateControllerResponseConstant.RESPONSE_DAILY_UPDATE_CREATED, response);
    }

    /**
     * Lists the updates the caller may read, newest first.
     */
    @GetMapping
    @Operation(summary = "List daily updates",
            description = "Updates of the caller and of colleagues sharing an ACTIVE organization, newest first; sortBy and sortDirection are ignored")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "422", description = "Invalid query or 'from' after 'to'"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<DailyUpdateResponse>>> listDailyUpdates(
            @Parameter(hidden = true) @GetUser User user,
            @Valid @ModelAttribute @ParameterObject DailyUpdateQueryRequest query) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_LIST_REQUEST);

        Page<DailyUpdateResponse> response = dailyUpdateQueryService.listDailyUpdates(user, query);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_LIST_COMPLETED);

        return ResponseUtil.ok(DailyUpdateControllerResponseConstant.RESPONSE_DAILY_UPDATE_LIST_RETRIEVED, response);
    }

    /**
     * Retrieves one update the caller may read.
     */
    @GetMapping("/{updateId}")
    @Operation(summary = "Get daily update")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "404", description = "Update not found or not visible to the caller"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DailyUpdateResponse>> getDailyUpdate(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID updateId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_GET_REQUEST);

        DailyUpdateResponse response = dailyUpdateQueryService.getDailyUpdate(user, updateId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_GET_COMPLETED);

        return ResponseUtil.ok(DailyUpdateControllerResponseConstant.RESPONSE_DAILY_UPDATE_RETRIEVED, response);
    }

    /**
     * Previews an edit of the caller's own update without saving it.
     */
    @PostMapping("/{updateId}/preview")
    @Operation(summary = "Preview daily update edit",
            description = "Returns the lines and summary text the edit would produce; progressSynced tells which lines would set ticket progress. Saves nothing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Caller is not the author"),
            @ApiResponse(responseCode = "404", description = "Update not visible, or added ticket not found or not on a board of the caller"),
            @ApiResponse(responseCode = "422", description = "Invalid request or duplicate ticket"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DailyUpdatePreviewResponse>> previewDailyUpdateEdit(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID updateId,
            @RequestBody @Valid DailyUpdatePreviewRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_PREVIEW_EDIT_REQUEST);

        DailyUpdatePreviewResponse response = dailyUpdateQueryService.previewDailyUpdateEdit(user, updateId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_PREVIEW_EDIT_COMPLETED);

        return ResponseUtil.ok(DailyUpdateControllerResponseConstant.RESPONSE_DAILY_UPDATE_PREVIEWED, response);
    }

    /**
     * Replaces the content of the caller's own update.
     */
    @PutMapping("/{updateId}")
    @Operation(summary = "Edit daily update",
            description = "Full replacement: the tickets list is the new set of lines in order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Caller is not the author"),
            @ApiResponse(responseCode = "404", description = "Update not visible, or added ticket not found or not on a board of the caller"),
            @ApiResponse(responseCode = "422", description = "Invalid request or duplicate ticket"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<DailyUpdateResponse>> editDailyUpdate(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID updateId,
            @RequestBody @Valid DailyUpdateUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_EDIT_REQUEST);

        DailyUpdateResponse response = dailyUpdateService.editDailyUpdate(user, updateId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_EDIT_COMPLETED);

        return ResponseUtil.ok(DailyUpdateControllerResponseConstant.RESPONSE_DAILY_UPDATE_EDITED, response);
    }

    /**
     * Soft-deletes the caller's own update.
     */
    @DeleteMapping("/{updateId}")
    @Operation(summary = "Delete daily update", description = "Ticket progress is not reverted")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Caller is not the author"),
            @ApiResponse(responseCode = "404", description = "Update not found or not visible to the caller"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteDailyUpdate(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID updateId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_DELETE_REQUEST);

        dailyUpdateService.deleteDailyUpdate(user, updateId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                DailyUpdateControllerLogConstant.DAILY_UPDATE_DELETE_COMPLETED);

        return ResponseUtil.ok(DailyUpdateControllerResponseConstant.RESPONSE_DAILY_UPDATE_DELETED);
    }
}
