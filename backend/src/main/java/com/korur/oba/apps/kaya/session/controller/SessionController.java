package com.korur.oba.apps.kaya.session.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
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
import com.korur.oba.apps.kaya.session.constant.SessionConstant;
import com.korur.oba.apps.kaya.session.constant.controller.SessionControllerLogConstant;
import com.korur.oba.apps.kaya.session.constant.controller.SessionControllerResponseConstant;
import com.korur.oba.apps.kaya.session.dto.request.SessionLinkCreateRequest;
import com.korur.oba.apps.kaya.session.dto.request.SessionQueryRequest;
import com.korur.oba.apps.kaya.session.dto.response.SessionDetailResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionFiltersResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionHandoffResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionLinkResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionListResponse;
import com.korur.oba.apps.kaya.session.service.SessionQueryService;
import com.korur.oba.apps.kaya.session.service.SessionService;

/**
 * Sessions of the organization named by the X-Organization-ID header, addressed by their Claude
 * Code session id: the wall, one session with its events and handoffs, its links, and its delete.
 * Deleting is open to "ADMIN or the session's user", so it takes MEMBER here and the service
 * checks the rest.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/sessions")
@Tag(name = "Sessions", description = "Recorded Claude Code sessions of the organization")
public class SessionController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SessionController.class);

    private final SessionService sessionService;
    private final SessionQueryService sessionQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the sessions of the organization for the wall.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the sessions of the organization",
            description = "Paged, 60 by default and at most; newest last event first. search matches the title, "
                    + "the branch or a linked Kazan ticket key")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a paging or filter value is invalid"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<SessionListResponse>>> getSessions(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject SessionQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.SESSION_LIST_REQUEST, organizationId);
        Page<SessionListResponse> response = sessionQueryService.listSessions(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.SESSION_LIST_COMPLETED,
                response.getNumberOfElements(), organizationId);
        return ResponseUtil.ok(SessionControllerResponseConstant.RESPONSE_SESSIONS_RETRIEVED, response);
    }

    /**
     * The options of the wall's filters.
     */
    @GetMapping("/filters")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read the session filter options",
            description = "The users, machines, models and Claude subscriptions the organization's sessions name; "
                    + "repositories come from GET /repositories")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SessionFiltersResponse>> getFilters(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.FILTERS_REQUEST, organizationId);
        SessionFiltersResponse response = sessionQueryService.getFilters(organizationId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.FILTERS_COMPLETED, organizationId);
        return ResponseUtil.ok(SessionControllerResponseConstant.RESPONSE_FILTERS_RETRIEVED, response);
    }

    /**
     * Reads one session.
     */
    @GetMapping("/{sessionId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a session",
            description = "With its links, subagents, skills used and handoff list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such session in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: the session id is malformed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SessionDetailResponse>> getSession(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable @Pattern(regexp = SessionConstant.SESSION_ID_PATTERN) String sessionId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.SESSION_GET_REQUEST, sessionId, organizationId);
        SessionDetailResponse response = sessionQueryService.getSession(organizationId, sessionId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.SESSION_GET_COMPLETED, sessionId, organizationId);
        return ResponseUtil.ok(SessionControllerResponseConstant.RESPONSE_SESSION_RETRIEVED, response);
    }

    /**
     * Reads the events of a session, all of them or those after a seq.
     */
    @GetMapping("/{sessionId}/events")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read the events of a session",
            description = "In seq order, each as the recorder wrote it; afterSeq reads only the newer events")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such session in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: the session id or afterSeq is malformed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<Map<String, Object>>>> getEvents(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable @Pattern(regexp = SessionConstant.SESSION_ID_PATTERN) String sessionId,
            @RequestParam(defaultValue = "0") @PositiveOrZero long afterSeq) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.EVENTS_REQUEST,
                sessionId, afterSeq, organizationId);
        List<Map<String, Object>> response = sessionQueryService.getEvents(organizationId, sessionId, afterSeq);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.EVENTS_COMPLETED,
                response.size(), sessionId, organizationId);
        return ResponseUtil.ok(SessionControllerResponseConstant.RESPONSE_EVENTS_RETRIEVED, response);
    }

    /**
     * Reads the handoff snapshots of a session with their content.
     */
    @GetMapping("/{sessionId}/handoffs")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read the handoffs of a session", description = "Each snapshot with its content and status, by path")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such session in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: the session id is malformed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<SessionHandoffResponse>>> getHandoffs(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable @Pattern(regexp = SessionConstant.SESSION_ID_PATTERN) String sessionId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.HANDOFFS_REQUEST, sessionId, organizationId);
        List<SessionHandoffResponse> response = sessionQueryService.getHandoffs(organizationId, sessionId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.HANDOFFS_COMPLETED,
                response.size(), sessionId, organizationId);
        return ResponseUtil.ok(SessionControllerResponseConstant.RESPONSE_HANDOFFS_RETRIEVED, response);
    }

    /**
     * Links a Kazan ticket or a pull request to a session.
     */
    @PostMapping("/{sessionId}/links")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Link a ticket or pull request to a session",
            description = "A Kazan ticket is its key, a pull request its https://github.com/ URL; "
                    + "adding a link the session has returns that link")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Link added, or the existing link returned"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such session in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a field is invalid; "
                    + "KAY_VAL_015: the reference does not fit its kind"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<SessionLinkResponse>> addLink(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable @Pattern(regexp = SessionConstant.SESSION_ID_PATTERN) String sessionId,
            @Valid @RequestBody SessionLinkCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.LINK_ADD_REQUEST,
                request.kind(), sessionId, organizationId);
        SessionLinkResponse response = sessionService.addLink(user, organizationId, sessionId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.LINK_ADD_COMPLETED,
                response.id(), sessionId, organizationId);
        return ResponseUtil.created(SessionControllerResponseConstant.RESPONSE_LINK_ADDED, response);
    }

    /**
     * Removes a link of a session.
     */
    @DeleteMapping("/{sessionId}/links/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Remove a link of a session",
            description = "Any link may be removed; a link the recorder found comes back if a later batch names it again")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such session or link"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: the session id is malformed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> removeLink(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable @Pattern(regexp = SessionConstant.SESSION_ID_PATTERN) String sessionId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.LINK_REMOVE_REQUEST, id, sessionId, organizationId);
        sessionService.removeLink(user, organizationId, sessionId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.LINK_REMOVE_COMPLETED, id, sessionId, organizationId);
        return ResponseUtil.ok(SessionControllerResponseConstant.RESPONSE_LINK_REMOVED);
    }

    /**
     * Deletes a session with everything recorded for it.
     */
    @DeleteMapping("/{sessionId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete a session", description = "ADMIN, or the session's own user; the delete cannot be undone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_003: neither ADMIN nor the session's user"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such session in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: the session id is malformed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteSession(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable @Pattern(regexp = SessionConstant.SESSION_ID_PATTERN) String sessionId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.SESSION_DELETE_REQUEST, sessionId, organizationId);
        sessionService.deleteSession(user, organizationId, sessionId);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.SESSION_DELETE_COMPLETED, sessionId, organizationId);
        return ResponseUtil.ok(SessionControllerResponseConstant.RESPONSE_SESSION_DELETED);
    }
}
