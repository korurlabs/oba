package com.korur.oba.apps.otak.event.controller;

// 1. Framework-level
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
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;

import jakarta.validation.Valid;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.constant.controller.EventControllerLogConstant;
import com.korur.oba.apps.otak.event.constant.controller.EventControllerResponseConstant;
import com.korur.oba.apps.otak.event.dto.request.EventCreateRequest;
import com.korur.oba.apps.otak.event.dto.request.EventQueryRequest;
import com.korur.oba.apps.otak.event.dto.request.EventUpdateRequest;
import com.korur.oba.apps.otak.event.dto.response.EventDetailResponse;
import com.korur.oba.apps.otak.event.dto.response.EventListResponse;
import com.korur.oba.apps.otak.event.service.EventQueryService;
import com.korur.oba.apps.otak.event.service.EventService;
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
 * REST controller for the events of an organization: the expos, conferences, meetups and
 * webinars where it meets companies. Every member reads and changes them; only an admin
 * deletes one.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/otak/events")
@Tag(name = "Otak events", description = "Events where an organization meets companies")
public class EventController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(EventController.class);

    private final EventService eventService;
    private final EventQueryService eventQueryService;
    private final LoggingService loggingService;

    /**
     * Lists one page of the events, optionally only the upcoming or the past ones.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List events",
            description = "One page of the organization's events with an optional name search. With period "
                    + "UPCOMING (last day today or later, soonest start first) or PAST (last day before today, most "
                    + "recent start first), today is the viewer's calendar day and required. Without period every "
                    + "event, most recent start first. sortBy and sortDirection are ignored.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "422", description = "Period without today (OTK_VAL_011), or invalid "
                    + "paging, period, today or search (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<EventListResponse>>> listEvents(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject EventQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, EventControllerLogConstant.EVENT_LIST_REQUEST);

        Page<EventListResponse> response = eventQueryService.list(organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, EventControllerLogConstant.EVENT_LIST_COMPLETED);

        return ResponseUtil.ok(EventControllerResponseConstant.RESPONSE_EVENT_LIST_RETRIEVED, response);
    }

    /**
     * Creates an event.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create event", description = "Creates an event of the organization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "409", description = "Another event of the organization has this name, case "
                    + "ignored (OTK_STA_013)"),
            @ApiResponse(responseCode = "422", description = "End date before the start date (OTK_VAL_010), "
                    + "or invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<EventDetailResponse>> createEvent(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid EventCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, EventControllerLogConstant.EVENT_CREATE_REQUEST);

        EventDetailResponse response = eventService.create(organizationId, user, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, EventControllerLogConstant.EVENT_CREATE_COMPLETED);

        return ResponseUtil.created(EventControllerResponseConstant.RESPONSE_EVENT_CREATED, response);
    }

    /**
     * Reads one event.
     */
    @GetMapping("/{eventId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get event", description = "Every field of one event of the organization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Event not found or of another organization (OTK_NOT_009)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<EventDetailResponse>> getEvent(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID eventId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, EventControllerLogConstant.EVENT_GET_REQUEST);

        EventDetailResponse response = eventQueryService.get(organizationId, eventId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, EventControllerLogConstant.EVENT_GET_COMPLETED);

        return ResponseUtil.ok(EventControllerResponseConstant.RESPONSE_EVENT_RETRIEVED, response);
    }

    /**
     * Replaces every field of an event.
     */
    @PutMapping("/{eventId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update event",
            description = "Replaces every field of an event: send the whole event, since null or a left-out "
                    + "optional field clears it.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Event not found or of another organization (OTK_NOT_009)"),
            @ApiResponse(responseCode = "409", description = "Another event of the organization has this name, case "
                    + "ignored (OTK_STA_013)"),
            @ApiResponse(responseCode = "422", description = "End date before the start date (OTK_VAL_010), "
                    + "or invalid request body (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<EventDetailResponse>> updateEvent(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID eventId,
            @RequestBody @Valid EventUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, EventControllerLogConstant.EVENT_UPDATE_REQUEST);

        EventDetailResponse response = eventService.update(organizationId, eventId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, EventControllerLogConstant.EVENT_UPDATE_COMPLETED);

        return ResponseUtil.ok(EventControllerResponseConstant.RESPONSE_EVENT_UPDATED, response);
    }

    /**
     * Deletes an event; its relations stay and lose the link.
     */
    @DeleteMapping("/{eventId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete event",
            description = "Removes an event; the relations linked to it stay and lose the link. Organization admins "
                    + "and owners only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not an admin of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Event not found or of another organization (OTK_NOT_009)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteEvent(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID eventId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, EventControllerLogConstant.EVENT_DELETE_REQUEST);

        eventService.delete(organizationId, eventId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, EventControllerLogConstant.EVENT_DELETE_COMPLETED);

        return ResponseUtil.ok(EventControllerResponseConstant.RESPONSE_EVENT_DELETED);
    }
}
