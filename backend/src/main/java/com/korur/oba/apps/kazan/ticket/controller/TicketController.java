package com.korur.oba.apps.kazan.ticket.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.korur.oba.apps.kazan.ticket.constant.controller.TicketControllerLogConstant;
import com.korur.oba.apps.kazan.ticket.constant.controller.TicketControllerResponseConstant;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketBulkMoveRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketCreateRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketMoveRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketOrderUpdateRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketQueryRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketUpdateRequest;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketBulkMoveResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketDetailResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketListResponse;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.apps.kazan.ticket.service.TicketService;
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
 * REST controller for ticket management endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/boards/{boardId}/tickets")
@Tag(name = "Tickets", description = "Ticket management APIs")
public class TicketController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(TicketController.class);

    private final TicketService ticketService;
    private final TicketQueryService ticketQueryService;
    private final LoggingService loggingService;

    /**
     * Creates a new ticket in a board.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create ticket")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<TicketDetailResponse>> createTicket(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @RequestBody @Valid TicketCreateRequest request) {
        // Log the incoming create request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_CREATE_REQUEST);

        // Delegate creation to the service layer.
        TicketDetailResponse response = ticketService.createTicket(user, organizationId, boardId, request);

        // Log the completed create operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_CREATE_COMPLETED);

        // Return a 201 created response with payload.
        return ResponseUtil.created(TicketControllerResponseConstant.RESPONSE_TICKET_CREATED, response);
    }

    /**
     * Lists tickets for a board with optional filters.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List tickets")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<TicketListResponse>>> listTickets(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            TicketQueryRequest queryRequest) {
        // Log the incoming list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_LIST_REQUEST);

        // Query tickets for the board.
        List<TicketListResponse> response = ticketQueryService.listTickets(user, organizationId, boardId, queryRequest);

        // Log the completed list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_LIST_COMPLETED);

        // Return a 200 OK response with list payload.
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_TICKET_LIST_RETRIEVED, response);
    }

    /**
     * Retrieves ticket details by id.
     */
    @GetMapping("/{ticketId}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get ticket details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<TicketDetailResponse>> getTicket(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID ticketId) {
        // Log the incoming detail request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_GET_REQUEST);

        // Query ticket detail.
        TicketDetailResponse response = ticketQueryService.getTicket(user, organizationId, boardId, ticketId);

        // Log the completed detail operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_GET_COMPLETED);

        // Return a 200 OK response with detail payload.
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_TICKET_RETRIEVED, response);
    }

    /**
     * Updates ticket metadata.
     */
    @PutMapping("/{ticketId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update ticket")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<TicketDetailResponse>> updateTicket(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID ticketId,
            @RequestBody @Valid TicketUpdateRequest request) {
        // Log the incoming update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_UPDATE_REQUEST);

        // Delegate update to the service layer.
        TicketDetailResponse response = ticketService.updateTicket(user, organizationId, boardId, ticketId, request);

        // Log the completed update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_UPDATE_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_TICKET_UPDATED, response);
    }

    /**
     * Deletes a ticket.
     */
    @DeleteMapping("/{ticketId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete ticket")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteTicket(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID ticketId) {
        // Log the incoming delete request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_DELETE_REQUEST);

        // Delegate delete operation to the service layer.
        ticketService.deleteTicket(user, organizationId, boardId, ticketId);

        // Log the completed delete operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_DELETE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_TICKET_DELETED);
    }

    /**
     * Moves a ticket to a different sprint or status.
     */
    @PostMapping("/{ticketId}/move")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Move ticket")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<TicketDetailResponse>> moveTicket(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID ticketId,
            @RequestBody @Valid TicketMoveRequest request) {
        // Log the incoming move request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_MOVE_REQUEST);

        // Delegate move operation to the service layer.
        TicketDetailResponse response = ticketService.moveTicket(user, organizationId, boardId, ticketId, request);

        // Log the completed move operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_MOVE_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_TICKET_MOVED, response);
    }

    /**
     * Updates the order of a ticket.
     */
    @PutMapping("/{ticketId}/order")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update ticket order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<TicketDetailResponse>> updateOrder(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID ticketId,
            @RequestBody @Valid TicketOrderUpdateRequest request) {
        // Log the incoming update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_UPDATE_REQUEST);

        // Delegate order update to the service layer.
        TicketDetailResponse response = ticketService.updateOrder(user, organizationId, boardId, ticketId, request);

        // Log the completed update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_UPDATE_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_TICKET_UPDATED, response);
    }

    /**
     * Restores a soft-deleted ticket and all its cascade-deleted children.
     */
    @PostMapping("/{ticketId}/restore")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Restore ticket")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> restoreTicket(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID ticketId) {
        // Log the incoming restore request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_RESTORE_REQUEST);

        // Delegate restore to the service layer.
        ticketService.restoreTicket(user, organizationId, boardId, ticketId);

        // Log the completed restore operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_RESTORE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_TICKET_RESTORED);
    }

    /**
     * Bulk-moves tickets to a different sprint or backlog.
     */
    @PostMapping("/bulk-move")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Bulk move tickets")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Board role too low (KAZ_SEC_002), GUEST role in the "
                    + "organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or "
                    + "the caller is not a member of it, or ticket not found (KAZ_NOT_001, KAZ_NOT_002, "
                    + "KAZ_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<TicketBulkMoveResponse>> bulkMoveTickets(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @RequestBody @Valid TicketBulkMoveRequest request) {
        // Log the incoming bulk move request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_BULK_MOVE_REQUEST);

        // Delegate bulk move to the service layer.
        TicketBulkMoveResponse response = ticketService.bulkMoveTickets(user, organizationId, boardId, request);

        // Log the completed bulk move operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_BULK_MOVE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_TICKETS_BULK_MOVED, response);
    }

}
