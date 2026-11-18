package com.korur.oba.connect.realtime.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
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
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimeTicketResponse;
import com.korur.oba.connect.realtime.service.RealtimeTicketService;
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
 * Issues the one-time tickets that open a realtime socket.
 *
 * The client calls it before every connect and reconnect, then opens /ws?ticket=. The request
 * passes the resource server and
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} like every REST
 * call, so the ticket carries an access check the handshake itself cannot make.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(RealtimeConstant.TICKETS_PATH)
@Tag(name = "Realtime", description = "Realtime connection APIs")
public class RealtimeTicketController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RealtimeTicketController.class);

    private final RealtimeTicketService realtimeTicketService;
    private final LoggingService loggingService;

    /**
     * Issues a ticket for one WebSocket handshake in the header's organization.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Issue realtime ticket",
            description = "One-time ticket for GET /ws?ticket=, valid 30 seconds; the socket is bound to the "
                    + "X-Organization-ID organization until the access token expires")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, user, membership or "
                    + "organization disabled, or the access token has no expiry (RTM_SEC_002)"),
            @ApiResponse(responseCode = "429", description = "More than 5 unused tickets (GEN_API_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<RealtimeTicketResponse>> issueTicket(
            @Parameter(hidden = true) @GetUser(requireActive = true) User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RealtimeConstant.TICKET_ISSUE_REQUEST, organizationId);

        RealtimeTicketResponse response = realtimeTicketService.issueTicket(user, organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, RealtimeConstant.TICKET_ISSUE_COMPLETED, organizationId);

        return ResponseUtil.created(RealtimeConstant.RESPONSE_TICKET_ISSUED, response);
    }
}
