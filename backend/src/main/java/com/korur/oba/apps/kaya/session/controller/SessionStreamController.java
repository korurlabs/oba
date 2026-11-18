package com.korur.oba.apps.kaya.session.controller;

// 1. Framework-level
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.apps.kaya.session.constant.controller.SessionControllerLogConstant;
import com.korur.oba.apps.kaya.session.service.SessionLiveService;

/**
 * The live session stream of the organization named by the X-Organization-ID header. It is a
 * server-sent event stream, not a CustomResponse, because it stays open and carries one event per
 * change.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/stream")
@Tag(name = "Session stream", description = "Live changes to the organization's sessions")
public class SessionStreamController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SessionStreamController.class);

    private final SessionLiveService sessionLiveService;
    private final LoggingService loggingService;

    /**
     * Opens the stream.
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Stream session changes",
            description = "Server-sent events: session-updated with {sessionId, lastEventAt} after each committed change "
                    + "(lastEventAt null when the session was deleted), and heartbeat with {at} on open and every 25 seconds")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The stream is open"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public SseEmitter stream(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SessionControllerLogConstant.STREAM_REQUEST, user.getId(), organizationId);
        return sessionLiveService.subscribe(user, organizationId);
    }
}
