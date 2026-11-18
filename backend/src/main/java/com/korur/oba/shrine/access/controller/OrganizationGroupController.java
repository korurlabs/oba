package com.korur.oba.shrine.access.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.korur.oba.shrine.access.constant.controller.OrganizationGroupControllerLogConstant;
import com.korur.oba.shrine.access.constant.controller.OrganizationGroupControllerResponseConstant;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupCreateRequest;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupMemberAddRequest;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupUpdateRequest;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupMemberResponse;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupResponse;
import com.korur.oba.shrine.access.service.OrganizationGroupQueryService;
import com.korur.oba.shrine.access.service.OrganizationGroupService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * REST controller for the groups of an organization and their members.
 *
 * Groups decide which members reach which app, so only an OWNER or ADMIN of the organization may
 * read or change them. Each handler states that role with {@link RequiredUserAccess}, and the
 * services make no second organization check.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/organizations/{organizationId}/groups")
@Tag(name = "Organization Groups", description = "Organization group management APIs")
public class OrganizationGroupController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OrganizationGroupController.class);

    private final OrganizationGroupService organizationGroupService;
    private final OrganizationGroupQueryService organizationGroupQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the groups of an organization, with the "Everyone" group first.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "List organization groups")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<OrganizationGroupResponse>>> listGroups(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId) {
        // Log the incoming groups list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUPS_LIST_REQUEST,
                organizationId);

        // Query the groups of the organization.
        List<OrganizationGroupResponse> response = organizationGroupQueryService.listGroups(user, organizationId);

        // Log the completed groups list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUPS_LIST_REQUEST_COMPLETED,
                organizationId);

        // Return a 200 OK response with the groups.
        return ResponseUtil.ok(OrganizationGroupControllerResponseConstant.RESPONSE_GROUPS_RETRIEVED, response);
    }

    /**
     * Creates a custom group in an organization.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Create organization group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Malformed request body"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "409", description = "Group name already taken (APP_STA_002)"),
            @ApiResponse(responseCode = "422", description = "Invalid request fields"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<OrganizationGroupResponse>> createGroup(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @RequestBody @Valid OrganizationGroupCreateRequest request) {
        // Log the incoming group create request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_CREATE_REQUEST,
                organizationId);

        // Delegate group creation to the service layer.
        OrganizationGroupResponse response = organizationGroupService.createGroup(user, organizationId, request);

        // Log the completed group create operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_CREATE_REQUEST_COMPLETED,
                organizationId, response.id());

        // Return a 201 Created response with the new group.
        return ResponseUtil.created(OrganizationGroupControllerResponseConstant.RESPONSE_GROUP_CREATED, response);
    }

    /**
     * Replaces the name and description of a custom group.
     */
    @PutMapping("/{groupId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update organization group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Malformed request body"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Group not found (APP_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "The Everyone group cannot change (APP_STA_001), "
                    + "or the group name is already taken (APP_STA_002)"),
            @ApiResponse(responseCode = "422", description = "Invalid request fields"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<OrganizationGroupResponse>> updateGroup(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID groupId,
            @RequestBody @Valid OrganizationGroupUpdateRequest request) {
        // Log the incoming group update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_UPDATE_REQUEST,
                organizationId, groupId);

        // Delegate the group update to the service layer.
        OrganizationGroupResponse response = organizationGroupService.updateGroup(user, organizationId, groupId, request);

        // Log the completed group update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_UPDATE_REQUEST_COMPLETED,
                organizationId, groupId);

        // Return a 200 OK response with the updated group.
        return ResponseUtil.ok(OrganizationGroupControllerResponseConstant.RESPONSE_GROUP_UPDATED, response);
    }

    /**
     * Deletes a custom group with its member rows and app grants.
     */
    @DeleteMapping("/{groupId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete organization group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Group not found (APP_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "The Everyone group cannot change (APP_STA_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteGroup(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID groupId) {
        // Log the incoming group delete request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_DELETE_REQUEST,
                organizationId, groupId);

        // Delegate the group deletion to the service layer.
        organizationGroupService.deleteGroup(user, organizationId, groupId);

        // Log the completed group delete operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_DELETE_REQUEST_COMPLETED,
                organizationId, groupId);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(OrganizationGroupControllerResponseConstant.RESPONSE_GROUP_DELETED);
    }

    /**
     * Lists the members of a group. The "Everyone" group lists every active member of the organization.
     */
    @GetMapping("/{groupId}/members")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "List organization group members")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Group not found (APP_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<OrganizationGroupMemberResponse>>> listGroupMembers(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID groupId) {
        // Log the incoming group members list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_MEMBERS_LIST_REQUEST,
                organizationId, groupId);

        // Query the members of the group.
        List<OrganizationGroupMemberResponse> response =
                organizationGroupQueryService.listGroupMembers(user, organizationId, groupId);

        // Log the completed group members list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_MEMBERS_LIST_REQUEST_COMPLETED,
                organizationId, groupId);

        // Return a 200 OK response with the group members.
        return ResponseUtil.ok(OrganizationGroupControllerResponseConstant.RESPONSE_GROUP_MEMBERS_RETRIEVED, response);
    }

    /**
     * Adds organization members to a custom group and returns the full member list of the group.
     */
    @PostMapping("/{groupId}/members")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Add organization group members")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Malformed request body"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Group not found (APP_NOT_001), "
                    + "or a user is not a member of the organization (ORG_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "The Everyone group cannot change (APP_STA_001)"),
            @ApiResponse(responseCode = "422", description = "Invalid request fields"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<OrganizationGroupMemberResponse>>> addGroupMembers(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID groupId,
            @RequestBody @Valid OrganizationGroupMemberAddRequest request) {
        // Log the incoming add group members request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_MEMBERS_ADD_REQUEST,
                organizationId, groupId);

        // Delegate adding the members to the service layer.
        List<OrganizationGroupMemberResponse> response =
                organizationGroupService.addMembers(user, organizationId, groupId, request);

        // Log the completed add group members operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_MEMBERS_ADD_REQUEST_COMPLETED,
                organizationId, groupId);

        // Return a 200 OK response with the full member list of the group.
        return ResponseUtil.ok(OrganizationGroupControllerResponseConstant.RESPONSE_GROUP_MEMBERS_ADDED, response);
    }

    /**
     * Removes one user from a custom group.
     */
    @DeleteMapping("/{groupId}/members/{userId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Remove organization group member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "403", description = "Not an active OWNER or ADMIN of an active organization"),
            @ApiResponse(responseCode = "404", description = "Group not found (APP_NOT_001), "
                    + "or the user is not in the group (APP_NOT_003)"),
            @ApiResponse(responseCode = "409", description = "The Everyone group cannot change (APP_STA_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> removeGroupMember(
            @Parameter(hidden = true) @GetUser User user,
            @PathVariable UUID organizationId,
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
        // Log the incoming remove group member request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_MEMBER_REMOVE_REQUEST,
                organizationId, groupId, userId);

        // Delegate the member removal to the service layer.
        organizationGroupService.removeMember(user, organizationId, groupId, userId);

        // Log the completed remove group member operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                OrganizationGroupControllerLogConstant.GROUP_MEMBER_REMOVE_REQUEST_COMPLETED,
                organizationId, groupId, userId);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(OrganizationGroupControllerResponseConstant.RESPONSE_GROUP_MEMBER_REMOVED);
    }
}
