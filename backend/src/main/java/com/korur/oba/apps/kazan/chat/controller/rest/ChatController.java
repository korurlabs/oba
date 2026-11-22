package com.korur.oba.apps.kazan.chat.controller.rest;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.constant.controller.ChatControllerLogConstant;
import com.korur.oba.apps.kazan.chat.constant.controller.ChatControllerResponseConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageQueryRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatReactionRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatReadStateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatSearchRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMemberResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessagePageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatReactionSummaryResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.apps.kazan.chat.service.ChatMemberQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatMessageQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatMessageService;
import com.korur.oba.apps.kazan.chat.service.ChatReactionService;
import com.korur.oba.apps.kazan.chat.service.ChatReadStateQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatReadStateService;
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
 * REST controller for the chat channel of an organization: messages,
 * reactions, pins, search, members and unread counts.
 *
 * The organization is the request's X-Organization-ID header, which is
 * required; the services check the caller's access to it on every call.
 * Reading and marking read need an ACTIVE membership of any role in an ACTIVE
 * organization; sending, editing, deleting, reacting and pinning need role
 * MEMBER or higher. A message of another organization is reported as not
 * found. Thread routes live in {@link ChatThreadController}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/chat")
@Tag(name = "Chat", description = "Organization chat channel APIs")
public class ChatController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatController.class);

    private final ChatMessageService chatMessageService;
    private final ChatMessageQueryService chatMessageQueryService;
    private final ChatReactionService chatReactionService;
    private final ChatReadStateService chatReadStateService;
    private final ChatReadStateQueryService chatReadStateQueryService;
    private final ChatMemberQueryService chatMemberQueryService;
    private final LoggingService loggingService;

    /**
     * Lists a keyset page of a channel the caller can read.
     */
    @GetMapping("/messages")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List channel messages",
            description = "Keyset page, items oldest first: the newest messages without a cursor, messages strictly "
                    + "older than before or newer than after, or limit/2 on each side of and including around. "
                    + "Deleted thread roots appear as placeholders. Optional day (YYYY-MM-DD, a day in "
                    + "Europe/Istanbul) and authorId keep only the messages of that day and author, cursors included. "
                    + "Optional pullRequestStatuses (comma-separated DRAFT, OPEN, IN_REVIEW, MERGED, DISCUSS) keeps "
                    + "only pull request share entries whose live share has one of them, in every page direction; "
                    + "plain messages and deleted thread roots are then left out. The filters combine with AND")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed parameter"),
            @ApiResponse(responseCode = "403", description = "Membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Channel not found or not readable (KAZ_NOT_015), or cursor message not in it (KAZ_NOT_013)"),
            @ApiResponse(responseCode = "422", description = "More than one cursor (KAZ_VAL_049), limit outside 1 to 50, "
                    + "or an unknown pullRequestStatuses value (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatMessagePageResponse>> listChannelMessages(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject ChatMessageQueryRequest query) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_CHANNEL_MESSAGES_LIST_REQUEST);

        ChatMessagePageResponse response = chatMessageQueryService.listChannelMessages(user, organizationId, query);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_CHANNEL_MESSAGES_LIST_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_MESSAGES_RETRIEVED, response);
    }

    /**
     * Reads one live message as the caller sees it, as a client does after a
     * realtime signal.
     */
    @GetMapping("/messages/{messageId}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get chat message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Message absent, deleted or of another organization (KAZ_NOT_013), or its channel not readable (KAZ_NOT_015)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatMessageResponse>> getMessage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID messageId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MESSAGE_GET_REQUEST);

        ChatMessageResponse response = chatMessageQueryService.getMessage(user, organizationId, messageId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MESSAGE_GET_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_MESSAGE_RETRIEVED, response);
    }

    /**
     * Sends a message with its files to the channel or a thread. A failed
     * upload saves nothing.
     */
    @PostMapping(value = "/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Send chat message",
            description = "Multipart: part 'message' is the JSON ChatMessageCreateRequest, optional parts 'files' "
                    + "are up to 10 files. The clientNonce is echoed in the response and the realtime signal")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed body"),
            @ApiResponse(responseCode = "403", description = "Another user's daily update (KAZ_SEC_008), GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Channel (KAZ_NOT_015), ticket reference (KAZ_NOT_004), reply target (KAZ_NOT_013), thread of the channel (KAZ_NOT_014) or daily update of the organization (KAZ_NOT_011) not found"),
            @ApiResponse(responseCode = "409", description = "Daily update already in the chat (KAZ_STA_015)"),
            @ApiResponse(responseCode = "413", description = "Files exceed the upload limit"),
            @ApiResponse(responseCode = "422", description = "Invalid body, message limits (KAZ_VAL_043), mention of a user who cannot read the channel (KAZ_VAL_044) or may not open Kazan (APP_VAL_002), reply outside the conversation (KAZ_VAL_045) or daily update outside the daily updates channel (KAZ_VAL_054)"),
            @ApiResponse(responseCode = "429", description = "Sending too fast (GEN_API_004)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatMessageResponse>> createMessage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestPart("message") ChatMessageCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MESSAGE_CREATE_REQUEST);

        ChatMessageResponse response = chatMessageService.postMessage(user, organizationId, request, files);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_MESSAGE_CREATE_COMPLETED);

        return ResponseUtil.created(ChatControllerResponseConstant.RESPONSE_CHAT_MESSAGE_CREATED, response);
    }

    /**
     * Replaces the text of the caller's own message.
     */
    @PutMapping("/messages/{messageId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Edit chat message", description = "Author only; a daily update entry is edited through its daily update")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed body"),
            @ApiResponse(responseCode = "403", description = "Not the author (KAZ_SEC_004), GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Message (KAZ_NOT_013), its channel (KAZ_NOT_015) or ticket reference (KAZ_NOT_004) not found"),
            @ApiResponse(responseCode = "422", description = "Invalid body, message limits (KAZ_VAL_043), mention of a non-member (KAZ_VAL_044) or of a user who may not open Kazan (APP_VAL_002), or daily update entry (KAZ_VAL_047)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatMessageResponse>> editMessage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID messageId,
            @Valid @RequestBody ChatMessageUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MESSAGE_EDIT_REQUEST);

        ChatMessageResponse response = chatMessageService.editMessage(user, organizationId, messageId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MESSAGE_EDIT_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_MESSAGE_EDITED, response);
    }

    /**
     * Soft-deletes a message for everyone: the caller's own, or any message
     * when the caller is an owner or admin of the organization.
     */
    @DeleteMapping("/messages/{messageId}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Delete chat message",
            description = "Author, organization OWNER or ADMIN; a thread started on the message lives on; a daily update entry is deleted through its daily update")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Neither the author nor an organization OWNER or ADMIN (KAZ_SEC_007), GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Message (KAZ_NOT_013) or its channel (KAZ_NOT_015) not found"),
            @ApiResponse(responseCode = "422", description = "Daily update entry (KAZ_VAL_047)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteMessage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID messageId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MESSAGE_DELETE_REQUEST);

        chatMessageService.deleteMessage(user, organizationId, messageId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_MESSAGE_DELETE_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_MESSAGE_DELETED);
    }

    /**
     * Adds the caller's reaction with an emoji; reacting twice changes nothing.
     */
    @PostMapping("/messages/{messageId}/reactions")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Add chat reaction", description = "Returns the message's reactions after the change, in first-reaction order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed body"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Message (KAZ_NOT_013) or its channel (KAZ_NOT_015) not found"),
            @ApiResponse(responseCode = "422", description = "Blank emoji, not one emoji, or 20 distinct emoji reached (KAZ_VAL_046)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<ChatReactionSummaryResponse>>> addReaction(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID messageId,
            @Valid @RequestBody ChatReactionRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_REACTION_ADD_REQUEST);

        List<ChatReactionSummaryResponse> response =
                chatReactionService.addReaction(user, organizationId, messageId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_REACTION_ADD_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_REACTION_ADDED, response);
    }

    /**
     * Removes the caller's reaction with an emoji; removing an absent one
     * changes nothing.
     */
    @DeleteMapping("/messages/{messageId}/reactions")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Remove chat reaction", description = "Returns the message's reactions after the change, in first-reaction order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or emoji"),
            @ApiResponse(responseCode = "403", description = "GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Message (KAZ_NOT_013) or its channel (KAZ_NOT_015) not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<ChatReactionSummaryResponse>>> removeReaction(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID messageId,
            @Parameter(description = "The emoji to remove, URL-encoded") @RequestParam String emoji) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_REACTION_REMOVE_REQUEST);

        List<ChatReactionSummaryResponse> response =
                chatReactionService.removeReaction(user, organizationId, messageId, emoji);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_REACTION_REMOVE_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_REACTION_REMOVED, response);
    }

    /**
     * Pins a message; pinning a pinned message changes nothing.
     */
    @PutMapping("/messages/{messageId}/pin")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Pin chat message", description = "The author and the organization's OWNERs and ADMINs may pin; at most 50 pins per channel")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not the author, OWNER or ADMIN (KAZ_SEC_006), GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Message (KAZ_NOT_013) or its channel (KAZ_NOT_015) not found"),
            @ApiResponse(responseCode = "422", description = "50 pins reached (KAZ_VAL_048)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatMessageResponse>> pinMessage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID messageId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MESSAGE_PIN_REQUEST);

        ChatMessageResponse response = chatMessageService.setPinned(user, organizationId, messageId, true);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MESSAGE_PIN_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_MESSAGE_PINNED, response);
    }

    /**
     * Unpins a message; unpinning an unpinned message changes nothing.
     */
    @DeleteMapping("/messages/{messageId}/pin")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Unpin chat message", description = "The author and the organization's OWNERs and ADMINs may unpin")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Not the author, OWNER or ADMIN (KAZ_SEC_006), GUEST role, membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Message (KAZ_NOT_013) or its channel (KAZ_NOT_015) not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatMessageResponse>> unpinMessage(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID messageId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MESSAGE_UNPIN_REQUEST);

        ChatMessageResponse response = chatMessageService.setPinned(user, organizationId, messageId, false);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MESSAGE_UNPIN_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_MESSAGE_UNPINNED, response);
    }

    /**
     * Lists the organization's pinned messages, channel and threads, most
     * recently pinned first.
     */
    @GetMapping("/pins")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List pinned chat messages",
            description = "Newest pin first; before is the last pinned message of the previous page; hasOlder tells whether more pins follow")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed parameter"),
            @ApiResponse(responseCode = "403", description = "Membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Channel not found or not readable (KAZ_NOT_015), or cursor not a pinned message of it (KAZ_NOT_013)"),
            @ApiResponse(responseCode = "422", description = "Limit outside 1 to 50"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatMessagePageResponse>> listPinnedMessages(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Parameter(description = "Channel whose pins are listed, its threads included")
            @RequestParam UUID channelId,
            @Parameter(description = "Last pinned message of the previous page")
            @RequestParam(required = false) UUID before,
            @Parameter(description = "Page size, 1 to 50, default 50")
            @RequestParam(required = false) @Min(ChatConstant.MIN_PAGE_SIZE) @Max(ChatConstant.MAX_PAGE_SIZE) Integer limit) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_PINS_LIST_REQUEST);

        ChatMessagePageResponse response =
                chatMessageQueryService.listPinnedMessages(user, organizationId, channelId, before, limit);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_PINS_LIST_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_PINS_RETRIEVED, response);
    }

    /**
     * Searches the live messages of the organization's channel and threads.
     */
    @GetMapping("/search")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Search chat messages",
            description = "Case-insensitive substring match, newest first; a daily update entry matches through its "
                    + "daily update's text; hasOlder tells whether more matches follow")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed parameter"),
            @ApiResponse(responseCode = "403", description = "Membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Named channel not readable (KAZ_NOT_015), or cursor message not found (KAZ_NOT_013)"),
            @ApiResponse(responseCode = "422", description = "q outside 2 to 100 characters or limit outside 1 to 50"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatMessagePageResponse>> searchMessages(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject ChatSearchRequest query) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_SEARCH_REQUEST);

        ChatMessagePageResponse response = chatMessageQueryService.searchMessages(user, organizationId, query);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_SEARCH_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_SEARCH_COMPLETED, response);
    }

    /**
     * Lists the organization's active members for the mention list and the
     * member popover.
     */
    @GetMapping("/members")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List chat members",
            description = "ACTIVE members with an ACTIVE account, online first, then by first name, last name and "
                    + "email; search matches the name or the email, case-insensitive. kazanAccess false marks a "
                    + "member who may not open Kazan and cannot be mentioned (APP_VAL_002)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Not a member, or named channel not readable (KAZ_NOT_015)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<ChatMemberResponse>>> listMembers(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Parameter(description = "Text matched against the first name, last name, full name and email")
            @RequestParam(required = false) String search,
            @Parameter(description = "Channel whose readers are listed; none lists the organization")
            @RequestParam(required = false) UUID channelId,
            @Parameter(description = "True to list only the distinct authors of live messages in the channel")
            @RequestParam(defaultValue = "false") boolean authorsOnly) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MEMBERS_LIST_REQUEST);

        List<ChatMemberResponse> response = chatMemberQueryService.listMembers(user, organizationId, search,
                channelId, authorsOnly);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_MEMBERS_LIST_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_MEMBERS_RETRIEVED, response);
    }

    /**
     * Reads the caller's unread counts for the sidebar badge.
     */
    @GetMapping("/unread")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Get chat unread counts",
            description = "Channel count and read position, counts of the caller's active threads (not closed, not archived) above zero, "
                    + "and their total; each count stops at 100")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Not a member"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatUnreadResponse>> getUnread(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_UNREAD_GET_REQUEST);

        ChatUnreadResponse response = chatReadStateQueryService.getUnread(user, organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_UNREAD_GET_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_UNREAD_RETRIEVED, response);
    }

    /**
     * Marks the channel, or a thread, read up to a message, forward only.
     */
    @PutMapping("/read-state")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Mark chat read",
            description = "threadId null marks the channel; an older message than the stored position changes nothing. "
                    + "Returns the caller's unread counts after the change")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed body"),
            @ApiResponse(responseCode = "403", description = "Membership or organization disabled"),
            @ApiResponse(responseCode = "404", description = "Channel (KAZ_NOT_015), thread of the channel (KAZ_NOT_014) or message of that conversation (KAZ_NOT_013) not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatUnreadResponse>> markRead(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestBody ChatReadStateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_READ_STATE_UPDATE_REQUEST);

        ChatUnreadResponse response = chatReadStateService.markRead(user, organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_READ_STATE_UPDATE_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_READ_STATE_UPDATED, response);
    }
}
