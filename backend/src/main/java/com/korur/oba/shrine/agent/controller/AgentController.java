package com.korur.oba.shrine.agent.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.agent.constant.controller.AgentControllerLogConstant;
import com.korur.oba.shrine.agent.constant.controller.AgentControllerResponseConstant;
import com.korur.oba.shrine.agent.dto.request.AgentCreateRequest;
import com.korur.oba.shrine.agent.dto.request.AgentStatusUpdateRequest;
import com.korur.oba.shrine.agent.dto.request.AgentUpdateRequest;
import com.korur.oba.shrine.agent.dto.response.AgentOptionsResponse;
import com.korur.oba.shrine.agent.dto.response.AgentResponse;
import com.korur.oba.shrine.agent.dto.response.AgentTokenCreatedResponse;
import com.korur.oba.shrine.agent.routine.dto.request.AgentRoutineSetRequest;
import com.korur.oba.shrine.agent.routine.service.AgentRoutineService;
import com.korur.oba.shrine.agent.service.AgentQueryService;
import com.korur.oba.shrine.agent.service.AgentService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * REST controller for the agents of an organization, their API tokens and their routines.
 *
 * An agent acts in the organization as a member with its own grants, so registering, changing and
 * deleting one, issuing its token and setting its routine, is for an OWNER or ADMIN only. Each handler
 * states that role with {@link RequiredUserAccess}; the services look every agent up within the
 * organization, so an id of a person or of another organization's agent answers 404 AGT_NOT_001.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/organizations/{organizationId}/agents")
@Tag(name = "Agents", description = "Organization agent and agent token APIs")
public class AgentController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AgentController.class);

    private final AgentService agentService;
    private final AgentQueryService agentQueryService;
    private final AgentRoutineService agentRoutineService;
    private final LoggingService loggingService;

    /**
     * Lists the agents of the organization, disabled ones included, with their apps and live token.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "List agents")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<AgentResponse>>> listAgents(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENTS_LIST_REQUEST,
                organizationId);

        List<AgentResponse> response = agentQueryService.listAgents(user, organizationId);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENTS_LIST_REQUEST_COMPLETED,
                organizationId, response.size());

        return ResponseUtil.ok(AgentControllerResponseConstant.RESPONSE_AGENTS_RETRIEVED, response);
    }

    /**
     * Lists every flow with the providers it accepts, read from the connectors of the application.
     *
     * The options are the same in every organization, so the handler takes no organization id. The
     * organization in the path still decides access: only an OWNER or ADMIN of it, who may register an
     * agent there, reads them.
     */
    @GetMapping("/options")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "List agent flow and provider options")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AgentOptionsResponse>> getOptions() {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_OPTIONS_REQUEST);

        AgentOptionsResponse response = agentQueryService.getOptions();

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_OPTIONS_REQUEST_COMPLETED);

        return ResponseUtil.ok(AgentControllerResponseConstant.RESPONSE_AGENT_OPTIONS_RETRIEVED, response);
    }

    /**
     * Registers an agent: an active member of the organization with one grant per app.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Create an agent")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Malformed request body or unknown enum value"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "409", description = "Another agent of the organization has the name (AGT_STA_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request fields, or the flow does not accept the provider (AGT_VAL_003)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AgentResponse>> createAgent(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @RequestBody @Valid AgentCreateRequest request) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_CREATE_REQUEST,
                organizationId, request.flow(), request.provider());

        AgentResponse response = agentService.createAgent(user, organizationId, request);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_CREATE_REQUEST_COMPLETED,
                organizationId, response.id());

        return ResponseUtil.created(AgentControllerResponseConstant.RESPONSE_AGENT_CREATED, response);
    }

    /**
     * Changes the name, role and apps of an agent; flow and provider are fixed at creation.
     */
    @PutMapping("/{agentId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update an agent")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Malformed request body or unknown enum value"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Agent not found in the organization (AGT_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "Another agent of the organization has the name (AGT_STA_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request fields"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AgentResponse>> updateAgent(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID agentId,
            @RequestBody @Valid AgentUpdateRequest request) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_UPDATE_REQUEST,
                organizationId, agentId);

        AgentResponse response = agentService.updateAgent(user, organizationId, agentId, request);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_UPDATE_REQUEST_COMPLETED,
                organizationId, agentId);

        return ResponseUtil.ok(AgentControllerResponseConstant.RESPONSE_AGENT_UPDATED, response);
    }

    /**
     * Enables or disables an agent; a disabled agent's token is refused until it is enabled again.
     */
    @PatchMapping("/{agentId}/status")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Enable or disable an agent")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Malformed request body or unknown status"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Agent not found in the organization (AGT_NOT_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request fields"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AgentResponse>> updateAgentStatus(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID agentId,
            @RequestBody @Valid AgentStatusUpdateRequest request) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_STATUS_REQUEST,
                organizationId, agentId, request.status());

        AgentResponse response = agentService.updateStatus(user, organizationId, agentId, request);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_STATUS_REQUEST_COMPLETED,
                organizationId, agentId);

        return ResponseUtil.ok(AgentControllerResponseConstant.RESPONSE_AGENT_STATUS_UPDATED, response);
    }

    /**
     * Deletes an agent: revokes its token and removes its grants, membership and user.
     */
    @DeleteMapping("/{agentId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete an agent")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Agent not found in the organization (AGT_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteAgent(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID agentId) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_DELETE_REQUEST,
                organizationId, agentId);

        agentService.deleteAgent(user, organizationId, agentId);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_DELETE_REQUEST_COMPLETED,
                organizationId, agentId);

        return ResponseUtil.ok(AgentControllerResponseConstant.RESPONSE_AGENT_DELETED);
    }

    /**
     * Issues a token to a ROUTINE agent and revokes the one it had; the token is returned this once.
     */
    @PostMapping("/{agentId}/token")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Issue an agent token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Agent not found in the organization (AGT_NOT_001)"),
            @ApiResponse(responseCode = "422", description = "The agent runs on the API flow and takes no token (AGT_VAL_003)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AgentTokenCreatedResponse>> issueAgentToken(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID agentId) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_TOKEN_ISSUE_REQUEST,
                organizationId, agentId);

        AgentTokenCreatedResponse response = agentService.issueToken(user, organizationId, agentId);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_TOKEN_ISSUE_REQUEST_COMPLETED,
                organizationId, agentId);

        return ResponseUtil.created(AgentControllerResponseConstant.RESPONSE_AGENT_TOKEN_ISSUED, response);
    }

    /**
     * Revokes the agent's live token; an agent without one is left unchanged.
     */
    @DeleteMapping("/{agentId}/token")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Revoke an agent token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Agent not found in the organization (AGT_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> revokeAgentToken(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID agentId) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_TOKEN_REVOKE_REQUEST,
                organizationId, agentId);

        agentService.revokeToken(user, organizationId, agentId);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_TOKEN_REVOKE_REQUEST_COMPLETED,
                organizationId, agentId);

        return ResponseUtil.ok(AgentControllerResponseConstant.RESPONSE_AGENT_TOKEN_REVOKED);
    }

    /**
     * Sets the routine Oba fires when a ticket is assigned to a ROUTINE agent; the secret is stored
     * encrypted and the response shows only its prefix.
     */
    @PutMapping("/{agentId}/routine")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Set an agent routine")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Malformed request body"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Agent not found in the organization (AGT_NOT_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request fields, a routine id not in the provider's format (AGT_VAL_002), or an agent on the API flow (AGT_VAL_003)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AgentResponse>> setAgentRoutine(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID agentId,
            @RequestBody @Valid AgentRoutineSetRequest request) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_ROUTINE_SET_REQUEST,
                organizationId, agentId);

        AgentResponse response = agentRoutineService.set(user, organizationId, agentId, request);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_ROUTINE_SET_REQUEST_COMPLETED,
                organizationId, agentId);

        return ResponseUtil.ok(AgentControllerResponseConstant.RESPONSE_AGENT_ROUTINE_SET, response);
    }

    /**
     * Removes the agent's routine, so an assignment fires nothing; an agent without one is left unchanged.
     */
    @DeleteMapping("/{agentId}/routine")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Remove an agent routine")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Agent not found in the organization (AGT_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> removeAgentRoutine(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID agentId) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_ROUTINE_REMOVE_REQUEST,
                organizationId, agentId);

        agentRoutineService.remove(user, organizationId, agentId);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                AgentControllerLogConstant.AGENT_ROUTINE_REMOVE_REQUEST_COMPLETED,
                organizationId, agentId);

        return ResponseUtil.ok(AgentControllerResponseConstant.RESPONSE_AGENT_ROUTINE_REMOVED);
    }
}
