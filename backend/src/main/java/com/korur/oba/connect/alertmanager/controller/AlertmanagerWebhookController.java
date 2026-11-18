package com.korur.oba.connect.alertmanager.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.alertmanager.constant.controller.AlertmanagerControllerLogConstant;
import com.korur.oba.connect.alertmanager.constant.controller.AlertmanagerControllerResponseConstant;
import com.korur.oba.connect.alertmanager.dto.AlertmanagerPrincipal;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerWebhookRequest;
import com.korur.oba.connect.alertmanager.dto.response.AlertmanagerWebhookResponse;
import com.korur.oba.connect.alertmanager.service.AlertmanagerWebhookService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.ResponseUtil;

/**
 * Webhook that the in-cluster Alertmanager calls. It runs on its own security
 * chain without a JWT: the authentication filter checks the static bearer token,
 * so only Alertmanager reaches this controller, as the principal that carries the
 * organization bound to the token. What a notification causes is
 * decided by the app's
 * {@link com.korur.oba.connect.alertmanager.service.AlertmanagerNotificationHandler}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaizen/integrations/alertmanager")
@Tag(name = "Alertmanager integration", description = "Alertmanager webhook that turns alerts into incident tickets")
public class AlertmanagerWebhookController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AlertmanagerWebhookController.class);

    private final AlertmanagerWebhookService alertmanagerWebhookService;
    private final LoggingService loggingService;

    /**
     * Receives one Alertmanager notification for the configured incident board.
     */
    @PostMapping
    @Operation(summary = "Receive Alertmanager notification",
            description = "Creates a P1 INCIDENT ticket in the backlog of the configured incident board for each "
                    + "firing alert without an open ticket, at most 10 per notification. Repeated and non-firing "
                    + "alerts write nothing. Authorized by the static bearer token, not a JWT")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Invalid notification body"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or unconfigured bearer token, "
                    + "or no Alertmanager system actor"),
            @ApiResponse(responseCode = "403", description = "The token's organization is disabled (ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "The token's organization not found (ORG_NOT_001), "
                    + "incident board not configured or not found, or the board has no backlog"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AlertmanagerWebhookResponse>> receiveNotification(
            @Parameter(hidden = true) @AuthenticationPrincipal AlertmanagerPrincipal principal,
            @RequestBody @Valid AlertmanagerWebhookRequest request) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AlertmanagerControllerLogConstant.ALERTMANAGER_WEBHOOK_REQUEST,
                request.alerts().size());

        AlertmanagerWebhookResponse response = alertmanagerWebhookService.processNotification(principal.organizationId(), request);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AlertmanagerControllerLogConstant.ALERTMANAGER_WEBHOOK_COMPLETED,
                response.created(),
                response.ignored());

        return ResponseUtil.ok(AlertmanagerControllerResponseConstant.RESPONSE_ALERTS_PROCESSED, response);
    }
}
