package com.korur.oba.apps.kazan.chat.controller.rest;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.constant.controller.ChatControllerLogConstant;
import com.korur.oba.apps.kazan.chat.constant.controller.ChatControllerResponseConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageQueryRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatThreadCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatThreadStatusUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatThreadUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessagePageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatThreadPageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatThreadResponse;
import com.korur.oba.apps.kazan.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kazan.chat.service.ChatMessageQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatThreadQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatThreadService;
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
 * REST controller for chat threads: start, list, read, rename, change the
 * status, page through replies, join and leave.
 *
 * The organization is the request's X-Organization-ID header, which is
 * required. Reading needs an ACTIVE membership of any role in an ACTIVE
 * organization; every write needs role MEMBER or higher, and renaming and
 * changing the status need the thread's creator or an OWNER or ADMIN. A thread of another organization
 * is reported as not found.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/chat/threads")
@Tag(name = "Chat Threads", description = "Organization chat thread APIs")
public class ChatThreadController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatThreadController.class);

    private final ChatThreadService chatThreadService;
    private final ChatThreadQueryService chatThreadQueryService;
    private final ChatMessageQueryService chatMessageQueryService;
    private final LoggingService loggingService;

    /**
     * Starts a thread on a live channel message.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Create chat thread",
            description = "One thread per message; a null title takes the default derived from the root message. "
                    + "On 409 the error body carries the existing threadId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed body"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Root message (KAZ_NOT_013) or its channel (KAZ_NOT_015) not found"),
            @ApiResponse(responseCode = "409", description = "The message already has a thread (KAZ_STA_013)"),
            @ApiResponse(responseCode = "422", description = "Title outside 1 to 100 characters, or root is a thread message (KAZ_VAL_050)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatThreadResponse>> createThread(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestBody ChatThreadCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREAD_CREATE_REQUEST);

        ChatThreadResponse response = chatThreadService.createThread(user, organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREAD_CREATE_COMPLETED);

        return ResponseUtil.created(ChatControllerResponseConstant.RESPONSE_CHAT_THREAD_CREATED, response);
    }

    /**
     * Lists the threads with one effective status, most recently active first.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List chat threads",
            description = "Keyset page by last activity, filtered by effective status (an ACTIVE thread inactive longer "
                    + "than its auto-archive window lists as ARCHIVED); before is the last thread of the previous page; "
                    + "hasMore tells whether more threads follow")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed parameter"),
            @ApiResponse(responseCode = "403", description = "Membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Channel not found or not readable (KAZ_NOT_015), or cursor thread not in it (KAZ_NOT_014)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatThreadPageResponse>> listThreads(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Parameter(description = "Channel whose threads are listed")
            @RequestParam UUID channelId,
            @Parameter(description = "Effective status to list: ACTIVE (default), CLOSED or ARCHIVED")
            @RequestParam(defaultValue = "ACTIVE") ChatThreadStatus status,
            @Parameter(description = "Last thread of the previous page")
            @RequestParam(required = false) UUID before,
            @Parameter(description = "Page size, 1 to 50, default 50")
            @RequestParam(required = false) @Min(ChatConstant.MIN_PAGE_SIZE) @Max(ChatConstant.MAX_PAGE_SIZE) Integer limit) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREADS_LIST_REQUEST);

        ChatThreadPageResponse response =
                chatThreadQueryService.listThreads(user, organizationId, channelId, status, before, limit);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREADS_LIST_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_THREADS_RETRIEVED, response);
    }

    /**
     * Reads one thread for its panel.
     */
    @GetMapping("/{threadId}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get chat thread",
            description = "Root message (placeholder when deleted), every member, and the caller's membership and right to manage it")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Thread (KAZ_NOT_014) or its channel (KAZ_NOT_015) not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatThreadResponse>> getThread(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID threadId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREAD_GET_REQUEST);

        ChatThreadResponse response = chatThreadQueryService.getThread(user, organizationId, threadId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREAD_GET_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_THREAD_RETRIEVED, response);
    }

    /**
     * Renames a thread and sets its auto-archive window.
     */
    @PutMapping("/{threadId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Update chat thread",
            description = "Creator, OWNER or ADMIN only; autoArchiveMinutes is 60, 1440, 4320 or 10080")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed body"),
            @ApiResponse(responseCode = "403", description = "Not the creator, OWNER or ADMIN (KAZ_SEC_005), GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Thread (KAZ_NOT_014) or its channel (KAZ_NOT_015) not found"),
            @ApiResponse(responseCode = "422", description = "Blank or too long title, missing window, or window not allowed (KAZ_VAL_050)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatThreadResponse>> updateThread(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID threadId,
            @Valid @RequestBody ChatThreadUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREAD_UPDATE_REQUEST);

        ChatThreadResponse response = chatThreadService.updateThread(user, organizationId, threadId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREAD_UPDATE_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_THREAD_UPDATED, response);
    }

    /**
     * Closes, reopens, archives or unarchives a thread.
     */
    @PutMapping("/{threadId}/status")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Change chat thread status",
            description = "Creator, OWNER or ADMIN only. ACTIVE reopens or unarchives, CLOSED makes the thread "
                    + "read-only, ARCHIVED puts it away. A closed thread must be reopened before it can be "
                    + "archived. Asking for the status the thread already shows changes nothing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed body"),
            @ApiResponse(responseCode = "403", description = "Not the creator, OWNER or ADMIN (KAZ_SEC_005), GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Thread (KAZ_NOT_014) or its channel (KAZ_NOT_015) not found"),
            @ApiResponse(responseCode = "409", description = "ARCHIVED asked for a closed thread (KAZ_STA_014)"),
            @ApiResponse(responseCode = "422", description = "Missing status"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatThreadResponse>> changeThreadStatus(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID threadId,
            @Valid @RequestBody ChatThreadStatusUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_THREAD_STATUS_UPDATE_REQUEST);

        ChatThreadResponse response = chatThreadService.changeStatus(user, organizationId, threadId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_THREAD_STATUS_UPDATE_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_THREAD_STATUS_UPDATED, response);
    }

    /**
     * Lists a keyset page of a thread's messages.
     */
    @GetMapping("/{threadId}/messages")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List chat thread messages",
            description = "Same cursors and order as the channel list: items oldest first")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed parameter"),
            @ApiResponse(responseCode = "403", description = "Membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Thread (KAZ_NOT_014), its channel (KAZ_NOT_015) or cursor message (KAZ_NOT_013) not found"),
            @ApiResponse(responseCode = "422", description = "More than one cursor (KAZ_VAL_049) or limit outside 1 to 50"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatMessagePageResponse>> listThreadMessages(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID threadId,
            @Valid @ModelAttribute @ParameterObject ChatMessageQueryRequest query) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_THREAD_MESSAGES_LIST_REQUEST);

        ChatMessagePageResponse response =
                chatMessageQueryService.listThreadMessages(user, organizationId, threadId, query);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_THREAD_MESSAGES_LIST_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_THREAD_MESSAGES_RETRIEVED, response);
    }

    /**
     * Makes the caller a member of a thread; joining twice changes nothing.
     */
    @PutMapping("/{threadId}/membership")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Join chat thread")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Thread (KAZ_NOT_014) or its channel (KAZ_NOT_015) not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatThreadResponse>> joinThread(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID threadId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREAD_JOIN_REQUEST);

        ChatThreadResponse response = chatThreadService.joinThread(user, organizationId, threadId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREAD_JOIN_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_THREAD_JOINED, response);
    }

    /**
     * Removes the caller from a thread with their read position in it;
     * leaving a thread the caller does not follow changes nothing.
     */
    @DeleteMapping("/{threadId}/membership")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Leave chat thread")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Thread (KAZ_NOT_014) or its channel (KAZ_NOT_015) not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatThreadResponse>> leaveThread(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID threadId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREAD_LEAVE_REQUEST);

        ChatThreadResponse response = chatThreadService.leaveThread(user, organizationId, threadId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_THREAD_LEAVE_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_THREAD_LEFT, response);
    }
}
