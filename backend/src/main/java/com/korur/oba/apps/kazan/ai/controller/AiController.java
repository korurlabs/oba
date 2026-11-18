package com.korur.oba.apps.kazan.ai.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import java.util.Map;
import java.util.UUID;

import com.korur.oba.apps.kazan.ai.constant.controller.AiControllerLogConstant;
import com.korur.oba.apps.kazan.ai.constant.controller.AiControllerResponseConstant;
import com.korur.oba.apps.kazan.ai.dto.request.AiAnalyzeRequest;
import com.korur.oba.apps.kazan.ai.dto.request.AiCreateFromSuggestionsRequest;
import com.korur.oba.apps.kazan.ai.dto.response.AiAnalysisResponse;
import com.korur.oba.apps.kazan.ai.service.AiService;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketDetailResponse;
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

/**
 * REST controller for AI-powered ticket analysis and creation.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/boards/{boardId}/ai")
@Tag(name = "AI", description = "AI-powered ticket analysis and creation APIs")
public class AiController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AiController.class);

    private final AiService aiService;
    private final LoggingService loggingService;

    /**
     * Checks if AI features are available.
     */
    @GetMapping("/available")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Check AI availability")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), or the caller is not a member of it (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Map<String, Boolean>>> isAvailable(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId) {
        // Log the incoming availability check request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AiControllerLogConstant.AI_AVAILABLE_REQUEST);

        // Check AI availability (includes board access validation).
        boolean available = aiService.isAvailable(user, organizationId, boardId);

        // Log the completed availability check.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AiControllerLogConstant.AI_AVAILABLE_COMPLETED);

        // Return a 200 OK response with availability status.
        return ResponseUtil.ok(AiControllerResponseConstant.RESPONSE_AI_AVAILABLE, Map.of("available", available));
    }

    /**
     * Analyzes user input and suggests tickets using Claude AI.
     */
    @PostMapping("/analyze")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Analyze ticket with AI")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is a VIEWER of the board (KAZ_SEC_002), GUEST "
                    + "role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), or the caller is not a member of it (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AiAnalysisResponse>> analyzeTicket(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @RequestBody @Valid AiAnalyzeRequest request) {
        // Log the incoming analyze request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AiControllerLogConstant.AI_ANALYZE_REQUEST);

        // Delegate analysis to the service layer.
        AiAnalysisResponse response = aiService.analyzeTicket(user, organizationId, boardId, request);

        // Log the completed analyze operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AiControllerLogConstant.AI_ANALYZE_COMPLETED);

        // Return a 200 OK response with analysis result.
        return ResponseUtil.ok(AiControllerResponseConstant.RESPONSE_AI_ANALYZED, response);
    }

    /**
     * Creates tickets from AI-generated suggestions.
     */
    @PostMapping("/create-from-suggestions")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create tickets from AI suggestions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is a VIEWER of the board (KAZ_SEC_002), GUEST "
                    + "role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001), or the caller is not a member of it (KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<TicketDetailResponse>>> createFromSuggestions(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @RequestBody @Valid AiCreateFromSuggestionsRequest request) {
        // Log the incoming create from suggestions request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AiControllerLogConstant.AI_CREATE_REQUEST);

        // Delegate creation to the service layer.
        List<TicketDetailResponse> response = aiService.createFromSuggestions(user, organizationId, boardId, request);

        // Log the completed create operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AiControllerLogConstant.AI_CREATE_COMPLETED);

        // Return a 201 created response with created tickets.
        return ResponseUtil.created(AiControllerResponseConstant.RESPONSE_AI_TICKETS_CREATED, response);
    }
}
