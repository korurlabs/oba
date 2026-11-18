package com.korur.oba.apps.kazan.board.controller;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
import com.korur.oba.apps.kazan.board.constant.controller.BoardControllerLogConstant;
import com.korur.oba.apps.kazan.board.constant.controller.BoardControllerResponseConstant;
import com.korur.oba.apps.kazan.board.dto.request.BoardAddChildrenRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardCreateRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardDeleteRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardQueryRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardUpdateRequest;
import com.korur.oba.apps.kazan.board.dto.response.BoardDetailResponse;
import com.korur.oba.apps.kazan.board.dto.response.BoardListResponse;
import com.korur.oba.apps.kazan.board.dto.response.BoardListWithChildrenResponse;
import com.korur.oba.apps.kazan.board.service.BoardQueryService;
import com.korur.oba.apps.kazan.board.service.BoardService;
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
 * REST controller for board management endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/boards")
@Tag(name = "Boards", description = "Board management APIs")
public class BoardController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BoardController.class);

    private final BoardService boardService;
    private final BoardQueryService boardQueryService;
    private final LoggingService loggingService;

    /**
     * Creates a new board for the current user.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create board",
            description = "Creates a board of the X-Organization-ID organization with the caller as OWNER. The "
                    + "server derives the board's URL slug from its name.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the parent board "
                    + "(KAZ_SEC_002), GUEST role in the organization (ORG_SEC_003), not a member of it, or membership "
                    + "or organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Parent board not found or of another organization "
                    + "(KAZ_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "Prefix already in use (KAZ_STA_007), a concurrent "
                    + "write took the board's slug; retrying succeeds (KAZ_STA_017), or an initial member is not an "
                    + "active member of the organization (KAZ_STA_019)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001), or an initial member "
                    + "may not open Kazan in the organization (APP_VAL_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<BoardDetailResponse>> createBoard(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @RequestBody @Valid BoardCreateRequest request) {
        // Log the incoming create request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_CREATE_REQUEST);

        // Delegate creation to the service layer.
        BoardDetailResponse response = boardService.createBoard(user, organizationId, request);

        // Log the completed create operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_CREATE_COMPLETED);

        // Return a 201 created response with payload.
        return ResponseUtil.created(BoardControllerResponseConstant.RESPONSE_BOARD_CREATED, response);
    }

    /**
     * Lists boards for the current user.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List boards",
            description = "Lists the boards of the X-Organization-ID organization the caller is a member of.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<BoardListWithChildrenResponse>>> listBoards(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @ModelAttribute BoardQueryRequest request) {
        // Log the incoming list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_LIST_REQUEST);

        // Query boards for the current user.
        Page<BoardListWithChildrenResponse> response = boardQueryService.listBoards(user, organizationId, request);

        // Log the completed list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_LIST_COMPLETED);

        // Return a 200 OK response with list payload.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_LIST_RETRIEVED, response);
    }

    /**
     * Resolves a board by its URL slug among the boards the current user is a member of.
     */
    @GetMapping("/by-slug/{slug}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get board by slug",
            description = "Resolves a live board by its exact URL slug. A board the caller is not a member of "
                    + "reads as not found.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "No live board of the organization has the slug, or the "
                    + "caller is not a member of it (KAZ_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<BoardListResponse>> getBoardBySlug(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable String slug) {
        // Log the incoming get board by slug request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_GET_BY_SLUG_REQUEST);

        // Delegate to the query service.
        BoardListResponse response = boardQueryService.getBoardBySlug(user, organizationId, slug);

        // Log the completed get board by slug operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_GET_BY_SLUG_COMPLETED);

        // Return a 200 OK response with the resolved board.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_RETRIEVED, response);
    }

    /**
     * Returns a single board the current user is a member of.
     */
    @GetMapping("/{boardId}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get board",
            description = "Returns a live board by id. A board the caller is not a member of reads as not found.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Board id is not a UUID, or missing or malformed "
                    + "X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board absent, deleted or of another organization, or "
                    + "the caller is not a member of it (KAZ_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<BoardListResponse>> getBoard(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId) {
        // Log the incoming get board request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_GET_REQUEST);

        // Delegate to the query service.
        BoardListResponse response = boardQueryService.getBoard(user, organizationId, boardId);

        // Log the completed get board operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_GET_COMPLETED);

        // Return a 200 OK response with the board.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_RETRIEVED, response);
    }

    /**
     * Get related boards for the current user and selected board.
     */
    @GetMapping("/{boardId}/related")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List related boards")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization, or membership or "
                    + "organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization, or the "
                    + "caller is not a member of it (KAZ_NOT_001, KAZ_NOT_002)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<BoardListResponse>>> listRelatedBoards(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId) {
        // Log the incoming list request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_RELATED_LIST_REQUEST);

        // Query boards for the current user.
        List<BoardListResponse> response = boardQueryService.listRelatedBoards(user, organizationId, boardId);

        // Log the completed list operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_RELATED_LIST_COMPLETED);

        // Return a 200 OK response with list payload.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_RELATED_LIST_RETRIEVED, response);
    }

    /**
     * Updates board metadata.
     */
    @PutMapping("/{boardId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update board",
            description = "Partially updates a board. A rename moves the board to a slug derived from the new "
                    + "name unless the current slug already has the new name's base.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the board or of the "
                    + "new parent board (KAZ_SEC_002), GUEST role in the organization (ORG_SEC_003), not a member of "
                    + "it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board or new parent board not found or of another "
                    + "organization (KAZ_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "Prefix already in use (KAZ_STA_007), or a concurrent "
                    + "write took the board's new slug; retrying succeeds (KAZ_STA_017)"),
            @ApiResponse(responseCode = "422", description = "Invalid request body (GEN_VAL_001), or the new parent "
                    + "is the board itself or one of its descendants (KAZ_VAL_024)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<BoardDetailResponse>> updateBoard(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @RequestBody @Valid BoardUpdateRequest request) {
        // Log the incoming update request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_UPDATE_REQUEST);

        // Delegate update to the service layer.
        BoardDetailResponse response = boardService.updateBoard(user, organizationId, boardId, request);

        // Log the completed update operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_UPDATE_COMPLETED);

        // Return a 200 OK response with updated payload.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_UPDATED, response);
    }

    /**
     * Deletes a board with configurable handling for child boards and tickets.
     */
    @DeleteMapping("/{boardId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete board",
            description = "Soft-deletes a board. Query params control what happens to child boards and tickets; "
                    + "a new parent or ticket target board must belong to the same organization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not OWNER of the board, not ADMIN of the new "
                    + "parent or not MEMBER of the ticket target board (KAZ_SEC_002), GUEST role in the organization "
                    + "(ORG_SEC_003), not a member of it, or membership or organization disabled (ORG_SEC_001, "
                    + "ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board, new parent or target board not found or of "
                    + "another organization (KAZ_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteBoard(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @ModelAttribute BoardDeleteRequest request) {
        // Log the incoming delete request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_DELETE_REQUEST);

        // Delegate delete operation to the service layer.
        boardService.deleteBoard(user, organizationId, boardId, request);

        // Log the completed delete operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_DELETE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_DELETED);
    }

    /**
     * Restores a soft-deleted board and all its cascade-deleted children.
     */
    @PostMapping("/{boardId}/restore")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Restore board",
            description = "Restores a soft-deleted board along with all cascade-deleted children. When a live "
                    + "board took the board's slug meanwhile, the restored board gets the next free slug.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller was not OWNER of the board (KAZ_SEC_002), GUEST "
                    + "role in the organization (ORG_SEC_003), not a member of it, or membership or organization "
                    + "disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Board not found or of another organization "
                    + "(KAZ_NOT_001)"),
            @ApiResponse(responseCode = "409", description = "A concurrent write took the slug chosen for the "
                    + "restored board; retrying succeeds (KAZ_STA_017)"),
            @ApiResponse(responseCode = "422", description = "Board is not deleted (KAZ_VAL_020)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> restoreBoard(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId) {
        // Log the incoming restore request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_RESTORE_REQUEST);

        // Delegate restore operation to the service layer.
        boardService.restoreBoard(user, organizationId, boardId);

        // Log the completed restore operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_RESTORE_COMPLETED);

        // Return a 200 OK response indicating completion.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_RESTORED);
    }

    /**
     * Adds one or more existing boards as children of the specified parent board.
     */
    @PutMapping("/{boardId}/children")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Add child boards",
            description = "Sets one or more existing boards of the same organization as children of the specified "
                    + "parent board.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or OWNER of the parent or of a "
                    + "child board (KAZ_SEC_002), GUEST role in the organization (ORG_SEC_003), not a member of it, or "
                    + "membership or organization disabled (ORG_SEC_001, ORG_SEC_002, ORG_SEC_005)"),
            @ApiResponse(responseCode = "404", description = "Parent or child board not found or of another "
                    + "organization (KAZ_NOT_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<BoardDetailResponse>>> addChildBoards(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID boardId,
            @RequestBody @Valid BoardAddChildrenRequest request) {
        // Log the incoming add children request.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_ADD_CHILDREN_REQUEST);

        // Delegate to the service layer.
        List<BoardDetailResponse> response = boardService.addChildBoards(user, organizationId, boardId, request);

        // Log the completed add children operation.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.CONTROLLER,
                BoardControllerLogConstant.BOARD_ADD_CHILDREN_COMPLETED);

        // Return a 200 OK response with payload.
        return ResponseUtil.ok(BoardControllerResponseConstant.RESPONSE_BOARD_CHILDREN_ADDED, response);
    }

}
