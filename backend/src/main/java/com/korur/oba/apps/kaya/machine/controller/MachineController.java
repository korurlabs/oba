package com.korur.oba.apps.kaya.machine.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.apps.kaya.machine.constant.controller.MachineControllerLogConstant;
import com.korur.oba.apps.kaya.machine.constant.controller.MachineControllerResponseConstant;
import com.korur.oba.apps.kaya.machine.dto.request.MachineCreateRequest;
import com.korur.oba.apps.kaya.machine.dto.request.MachineQueryRequest;
import com.korur.oba.apps.kaya.machine.dto.request.MachineUpdateRequest;
import com.korur.oba.apps.kaya.machine.dto.response.MachineCreatedResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineTokenCreatedResponse;
import com.korur.oba.apps.kaya.machine.service.MachineQueryService;
import com.korur.oba.apps.kaya.machine.service.MachineService;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Machines of the organization named by the X-Organization-ID header: the machines that record
 * sessions for a user, and their recorder tokens. Actions open to "self" or "ADMIN or self" take
 * MEMBER here and check the rest in the service, because "self" depends on the machine's user.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/machines")
@Tag(name = "Machines", description = "Machines of the organization and their recorder tokens")
public class MachineController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MachineController.class);

    private final MachineService machineService;
    private final MachineQueryService machineQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the machines of the organization.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the machines of the organization",
            description = "Not paged; ordered by user display name and machine name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or a filter value malformed"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<MachineResponse>>> getMachines(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject MachineQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.MACHINE_LIST_REQUEST, organizationId);
        List<MachineResponse> response = machineQueryService.getMachines(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.MACHINE_LIST_COMPLETED,
                response.size(), organizationId);
        return ResponseUtil.ok(MachineControllerResponseConstant.RESPONSE_MACHINES_RETRIEVED, response);
    }

    /**
     * Reads one machine.
     */
    @GetMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read a machine")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such machine in the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MachineResponse>> getMachine(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.MACHINE_GET_REQUEST, id, organizationId);
        MachineResponse response = machineQueryService.getMachine(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.MACHINE_GET_COMPLETED, id, organizationId);
        return ResponseUtil.ok(MachineControllerResponseConstant.RESPONSE_MACHINE_RETRIEVED, response);
    }

    /**
     * Registers a machine and returns its recorder token once.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Register a machine",
            description = "For the caller, or as ADMIN for another user; audited as MACHINE_CREATED. "
                    + "A RETIRED machine with the same machine name is made ACTIVE again with a new token. "
                    + "The plaintext token is in this response only")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Machine registered"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_003: another user's machine and the caller is not ADMIN"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: the user is not a member of the organization"),
            @ApiResponse(responseCode = "409", description = "KAY_VAL_006: the user has an active machine with this machine name"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a field is invalid"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MachineCreatedResponse>> createMachine(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestBody MachineCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.MACHINE_CREATE_REQUEST, organizationId);
        MachineCreatedResponse response = machineService.createMachine(user, organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.MACHINE_CREATE_COMPLETED,
                response.machine().id(), organizationId);
        return ResponseUtil.created(MachineControllerResponseConstant.RESPONSE_MACHINE_CREATED, response);
    }

    /**
     * Rotates the token of a machine.
     */
    @PostMapping("/{id}/token")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Issue a new token to a machine",
            description = "Only the machine's own user; the live token is revoked in the same transaction. "
                    + "Audited as TOKEN_ROTATED, or TOKEN_ISSUED when there was no live token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Token issued"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_003: the caller is not the machine's user"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such machine in the organization"),
            @ApiResponse(responseCode = "409", description = "KAY_VAL_011: the machine is retired"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MachineTokenCreatedResponse>> rotateToken(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.TOKEN_ROTATE_REQUEST, id, organizationId);
        MachineTokenCreatedResponse response = machineService.rotateToken(user, organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.TOKEN_ROTATE_COMPLETED, id, organizationId);
        return ResponseUtil.created(MachineControllerResponseConstant.RESPONSE_TOKEN_ROTATED, response);
    }

    /**
     * Revokes the token of a machine.
     */
    @DeleteMapping("/{id}/token")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Revoke the token of a machine",
            description = "ADMIN or the machine's user; audited as TOKEN_REVOKED. Without a live token nothing changes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token revoked, or none was live"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_003: the caller is neither ADMIN nor the machine's user"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such machine in the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> revokeToken(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.TOKEN_REVOKE_REQUEST, id, organizationId);
        machineService.revokeToken(user, organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.TOKEN_REVOKE_COMPLETED, id, organizationId);
        return ResponseUtil.ok(MachineControllerResponseConstant.RESPONSE_TOKEN_REVOKED);
    }

    /**
     * Retires a machine.
     */
    @PatchMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Retire a machine",
            description = "ADMIN or the machine's user; revokes its token; audited as MACHINE_RETIRED")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Machine retired, or already retired"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or malformed body"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "GEN_SEC_003: the caller is neither ADMIN nor the machine's user"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such machine in the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: the status is missing or not RETIRED"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<MachineResponse>> updateMachine(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id,
            @Valid @RequestBody MachineUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.MACHINE_UPDATE_REQUEST, id, organizationId);
        MachineResponse response = machineService.updateMachine(user, organizationId, id, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, MachineControllerLogConstant.MACHINE_UPDATE_COMPLETED, id, organizationId);
        return ResponseUtil.ok(MachineControllerResponseConstant.RESPONSE_MACHINE_UPDATED, response);
    }
}
