package com.korur.oba.apps.kaizen.ticket.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.constant.controller.TicketControllerLogConstant;
import com.korur.oba.apps.kaizen.ticket.constant.controller.TicketControllerResponseConstant;
import com.korur.oba.apps.kaizen.ticket.dto.request.AllTicketQueryRequest;
import com.korur.oba.apps.kaizen.ticket.dto.response.AllTicketListResponse;
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
 * REST controller for cross-board ticket query endpoints.
 * <p>
 * Provides endpoints to list all tickets across all boards
 * where the authenticated user is a member.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaizen/tickets")
@Tag(name = "All Tickets", description = "Cross-board ticket query APIs")
public class AllTicketController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AllTicketController.class);

    private final TicketQueryService ticketQueryService;
    private final LoggingService loggingService;

    /**
     * Lists all tickets across boards where the authenticated user is a member.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List all tickets across boards")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<AllTicketListResponse>>> listAllTickets(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            AllTicketQueryRequest queryRequest) {
        // Log the incoming list all tickets request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.ALL_TICKET_LIST_REQUEST);

        // Delegate to the query service.
        List<AllTicketListResponse> response = ticketQueryService.listAllTickets(user.getId(), organizationId, queryRequest);

        // Log the completed list all tickets operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.ALL_TICKET_LIST_COMPLETED);

        // Return a 200 OK response with list payload.
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_ALL_TICKET_LIST_RETRIEVED, response);
    }

    /**
     * Resolves a single ticket by its exact key, for any ticket including
     * sub-tickets, scoped to boards where the authenticated user is a member.
     */
    @GetMapping("/by-key/{key}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Resolve a single ticket by its exact key")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "No ticket with the key on the caller's boards of "
                    + "the organization (KAI_NOT_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AllTicketListResponse>> getTicketByKey(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable String key) {
        // Log the incoming get ticket by key request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_GET_BY_KEY_REQUEST);

        // Delegate to the query service.
        AllTicketListResponse response = ticketQueryService.getTicketByKey(user.getId(), organizationId, key);

        // Log the completed get ticket by key operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                TicketControllerLogConstant.TICKET_GET_BY_KEY_COMPLETED);

        // Return a 200 OK response with the resolved ticket.
        return ResponseUtil.ok(TicketControllerResponseConstant.RESPONSE_TICKET_RETRIEVED, response);
    }
}
