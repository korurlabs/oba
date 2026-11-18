package com.korur.oba.apps.kazan.board.controller;

import java.util.List;
import java.util.UUID;

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

import com.korur.oba.apps.kazan.board.constant.controller.BoardControllerLogConstant;
import com.korur.oba.apps.kazan.board.constant.controller.BoardControllerResponseConstant;
import com.korur.oba.apps.kazan.board.dto.request.BoardMemberAddRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardMemberSpecialtyUpdateRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardMemberUpdateRequest;
import com.korur.oba.apps.kazan.board.dto.response.BoardMemberCandidateResponse;
import com.korur.oba.apps.kazan.board.dto.response.BoardMemberResponse;
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.board.service.BoardMemberService;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for board member management endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/boards/{boardId}/members")
@Tag(name = "Board Members", description = "Board member management APIs")
public class BoardMemberController {
    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BoardMemberController.class);

    private final LoggingService loggingService;

    private final BoardMemberQueryService boardMemberQueryService;
    private final BoardMemberService boardMemberService;

    /**
     * Lists all members in a specific board.
     *
     * Open to GUEST like every other read of a board: the read-only board and ticket
     * views take the caller's board role and the assignee and reviewer names from this
     * list. The caller must still be a member of the board.
     */
    @GetMapping()
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List board members",
            description = "Members who are not an active member of the board's organization are left out. "
                    + "kazanAccess tells whether the member may open Kazan in that organization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or the "
                    + "caller is not a member of it (KAZ_NOT_001, KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<BoardMemberResponse>>> listMembers(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId) {
        // Log the incoming members list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_LIST_REQUEST);

        // Query members for the board.
        List<BoardMemberResponse> response = boardMemberQueryService.listMembers(user, organizationId, boardId);

        // Log the completed members list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_LIST_COMPLETED);

        // Return a 200 OK response with members list.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_MEMBER_LIST_RETRIEVED, response);
    }

    /**
     * Lists the active members of the board's organization for the add-member picker, each with
     * whether it may open Kazan there. The organization is the board's, so a caller who
     * belongs to several organizations sees only the colleagues the board can take.
     */
    @GetMapping("/candidates")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List board member candidates",
            description = "ACTIVE members with an ACTIVE account of the board's organization, board members included, "
                    + "ordered by first name, last name and email. kazanAccess false marks a member that the add "
                    + "refuses with APP_VAL_002.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or the "
                    + "caller is not a member of it (KAZ_NOT_001, KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<BoardMemberCandidateResponse>>> listMemberCandidates(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId) {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_CANDIDATE_LIST_REQUEST);

        List<BoardMemberCandidateResponse> response =
                boardMemberQueryService.listMemberCandidates(user, organizationId, boardId);

        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_CANDIDATE_LIST_COMPLETED);

        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_MEMBER_CANDIDATES_RETRIEVED, response);
    }

    /**
     * Adds a new member to a board.
     */
    @PostMapping()
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Add board member",
            description = "The added user must be an active member of the board's organization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or the "
                    + "caller is not a member of it (KAZ_NOT_001, KAZ_NOT_002)"),
            @ApiResponse(responseCode = "409", description = "User is already a member of the board (KAZ_STA_003), or "
                    + "is not an active member of the board's organization (KAZ_STA_019)"),
            @ApiResponse(responseCode = "422", description = "User may not open Kazan in the board's organization "
                    + "(APP_VAL_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<BoardMemberResponse>> addMember(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @RequestBody @Valid BoardMemberAddRequest request) {
        // Log the incoming add member request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_ADD_REQUEST);

        // Delegate member addition to the service layer.
        BoardMemberResponse response = boardMemberService.addMember(user, organizationId, boardId, request);

        // Log the completed add member operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_ADD_COMPLETED);

        // Return a 201 created response with membership payload.
        return ResponseUtil.created(BoardControllerResponseConstant.RESPONSE_BOARD_MEMBER_ADDED, response);
    }

    /**
     * Removes an existing member from a board.
     */
    @DeleteMapping("/{userId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Remove board member")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or the "
                    + "caller or the target user is not a member of it (KAZ_NOT_001, KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> removeMember(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID userId) {
        // Log the incoming remove member request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_REMOVE_REQUEST);

        // Delegate member removal to the service layer.
        boardMemberService.removeMember(user, organizationId, boardId, userId);

        // Log the completed remove member operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_REMOVE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_MEMBER_REMOVED);
    }

    /**
     * Updates a member's role in a board.
     */
    @PutMapping("/{userId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update board member role")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or the "
                    + "caller or the target user is not a member of it (KAZ_NOT_001, KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<BoardMemberResponse>> updateMemberRole(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID userId,
            @RequestBody @Valid BoardMemberUpdateRequest request) {
        // Log the incoming update member request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_UPDATE_REQUEST);

        // Delegate member role update to the service layer.
        BoardMemberResponse response = boardMemberService.updateMemberRole(user, organizationId, boardId, userId, request);

        // Log the completed update member operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_UPDATE_COMPLETED);

        // Return a 200 OK response with updated member payload.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_MEMBER_UPDATED, response);
    }

    /**
     * Updates a board member's specialties.
     */
    @PutMapping("/{userId}/specialties")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update board member specialties")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board (KAZ_SEC_002), "
                    + "GUEST role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or the "
                    + "caller or the target user is not a member of it (KAZ_NOT_001, KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> updateMemberSpecialties(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @PathVariable UUID userId,
            @RequestBody @Valid BoardMemberSpecialtyUpdateRequest request) {
        // Log the incoming specialty update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_SPECIALTY_UPDATE_REQUEST);

        // Delegate specialty update to the service layer.
        boardMemberService.updateMemberSpecialties(user, organizationId, boardId, userId, request.specialties());

        // Log the completed specialty update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_MEMBER_SPECIALTY_UPDATE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_MEMBER_SPECIALTIES_UPDATED);
    }
}
