package com.korur.oba.apps.kazan.chat.controller.rest;

// 1. Framework-level
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
import com.korur.oba.apps.kazan.chat.constant.controller.ChatControllerLogConstant;
import com.korur.oba.apps.kazan.chat.constant.controller.ChatControllerResponseConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatChannelCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatChannelMembersRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatChannelUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatChannelResponse;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatChannelService;
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
 * REST controller for chat channels: list, create, change and delete a
 * channel, and manage a private channel's member list.
 *
 * The organization is the request's X-Organization-ID header, which is
 * required. Listing needs an ACTIVE membership of any role in an ACTIVE
 * organization; every change needs an OWNER or ADMIN, except that any member
 * may leave a private channel. A channel of another organization, and a
 * private channel a member is not on, is reported as not found.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/chat/channels")
@Tag(name = "Chat Channels", description = "Organization chat channel APIs")
public class ChatChannelController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatChannelController.class);

    private final ChatChannelService chatChannelService;
    private final ChatChannelQueryService chatChannelQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the channels the caller may see, in creation order.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "List chat channels",
            description = "Public channels, the private channels the caller is on, and for OWNER and ADMIN every "
                    + "private channel with member false where they are not on its list; oldest first")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Membership or organization disabled"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<ChatChannelResponse>>> listChannels(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_CHANNELS_LIST_REQUEST);

        List<ChatChannelResponse> response = chatChannelQueryService.listChannels(user, organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_CHANNELS_LIST_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_CHANNELS_RETRIEVED, response);
    }

    /**
     * Creates a text channel.
     */
    @PostMapping
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Create chat channel",
            description = "OWNER or ADMIN only; a private channel's members are the creator plus memberIds; "
                    + "a purpose other than GENERAL (DAILY_UPDATES, PULL_REQUESTS) needs a public channel and is "
                    + "refused while another live channel holds it. The server derives the channel's URL slug from "
                    + "its name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed body"),
            @ApiResponse(responseCode = "403", description = "MEMBER or GUEST role, membership or organization disabled (ORG_SEC_003)"),
            @ApiResponse(responseCode = "422", description = "A member is no active member of the organization (KAZ_VAL_044) or may not open Kazan (APP_VAL_002), "
                    + "memberIds on a public channel (KAZ_VAL_051), or a purpose other than GENERAL on a private "
                    + "channel (KAZ_VAL_053)"),
            @ApiResponse(responseCode = "409", description = "Another live channel holds the requested purpose (KAZ_STA_016), "
                    + "or a concurrent write took the channel's slug; retrying succeeds (KAZ_STA_017)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatChannelResponse>> createChannel(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @RequestBody ChatChannelCreateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_CHANNEL_CREATE_REQUEST);

        ChatChannelResponse response = chatChannelService.createChannel(user, organizationId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_CHANNEL_CREATE_COMPLETED);

        return ResponseUtil.created(ChatControllerResponseConstant.RESPONSE_CHAT_CHANNEL_CREATED, response);
    }

    /**
     * Applies a full update of name, visibility and purpose.
     */
    @PutMapping("/{channelId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Update chat channel",
            description = "OWNER or ADMIN only; the type never changes. Public to private makes the caller plus "
                    + "memberIds the members; private to public removes the member list and shows every message to "
                    + "the organization. Taking a purpose other than GENERAL (DAILY_UPDATES, PULL_REQUESTS) is "
                    + "refused while another live channel holds it; the channel holding it keeps it. A rename moves "
                    + "the channel's slug unless the slug already has the new name's base")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed body"),
            @ApiResponse(responseCode = "403", description = "MEMBER or GUEST role, membership or organization disabled (ORG_SEC_003)"),
            @ApiResponse(responseCode = "404", description = "Channel not found (KAZ_NOT_015)"),
            @ApiResponse(responseCode = "422", description = "A member is no active member of the organization (KAZ_VAL_044) or may not open Kazan (APP_VAL_002), "
                    + "memberIds outside a switch to private (KAZ_VAL_051), or a private channel with a purpose "
                    + "other than GENERAL (KAZ_VAL_053)"),
            @ApiResponse(responseCode = "409", description = "Another live channel holds the requested purpose (KAZ_STA_016), "
                    + "or a concurrent write took the channel's new slug; retrying succeeds (KAZ_STA_017)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatChannelResponse>> updateChannel(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID channelId,
            @Valid @RequestBody ChatChannelUpdateRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_CHANNEL_UPDATE_REQUEST);

        ChatChannelResponse response = chatChannelService.updateChannel(user, organizationId, channelId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_CHANNEL_UPDATE_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_CHANNEL_UPDATED, response);
    }

    /**
     * Soft-deletes a channel; its content is kept and hidden.
     */
    @DeleteMapping("/{channelId}")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Delete chat channel",
            description = "OWNER or ADMIN only; deleting the daily updates channel leaves daily updates without a chat card")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "MEMBER or GUEST role, membership or organization disabled (ORG_SEC_003)"),
            @ApiResponse(responseCode = "404", description = "Channel not found (KAZ_NOT_015)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> deleteChannel(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID channelId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_CHANNEL_DELETE_REQUEST);

        chatChannelService.deleteChannel(user, organizationId, channelId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, ChatControllerLogConstant.CHAT_CHANNEL_DELETE_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_CHANNEL_DELETED);
    }

    /**
     * Adds users to a private channel's member list.
     */
    @PutMapping("/{channelId}/members")
    @RequiredUserAccess(OrganizationMemberRole.ADMIN)
    @Operation(summary = "Add chat channel members",
            description = "OWNER or ADMIN only; users already on the list stay as they are")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header or malformed body"),
            @ApiResponse(responseCode = "403", description = "MEMBER or GUEST role, membership or organization disabled (ORG_SEC_003)"),
            @ApiResponse(responseCode = "404", description = "Channel not found (KAZ_NOT_015)"),
            @ApiResponse(responseCode = "422", description = "A user is no active member of the organization (KAZ_VAL_044) or may not open Kazan (APP_VAL_002), "
                    + "or the channel is public (KAZ_VAL_052)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<ChatChannelResponse>> addMembers(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID channelId,
            @Valid @RequestBody ChatChannelMembersRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_CHANNEL_MEMBERS_ADD_REQUEST);

        ChatChannelResponse response = chatChannelService.addMembers(user, organizationId, channelId, request);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_CHANNEL_MEMBERS_ADD_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_CHANNEL_MEMBERS_ADDED, response);
    }

    /**
     * Removes a user from a private channel's member list; a member removing
     * themselves leaves the channel.
     */
    @DeleteMapping("/{channelId}/members/{userId}")
    @RequiredUserAccess(OrganizationMemberRole.GUEST)
    @Operation(summary = "Remove chat channel member",
            description = "OWNER or ADMIN removes anyone; any member may remove themselves to leave")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "Removing another user without OWNER or ADMIN (KAZ_SEC_011), membership or organization disabled (ORG_SEC_003)"),
            @ApiResponse(responseCode = "404", description = "Channel not found (KAZ_NOT_015)"),
            @ApiResponse(responseCode = "422", description = "The channel is public (KAZ_VAL_052)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<Void>> removeMember(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID channelId,
            @PathVariable UUID userId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_CHANNEL_MEMBER_REMOVE_REQUEST);

        chatChannelService.removeMember(user, organizationId, channelId, userId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                ChatControllerLogConstant.CHAT_CHANNEL_MEMBER_REMOVE_COMPLETED);

        return ResponseUtil.ok(ChatControllerResponseConstant.RESPONSE_CHAT_CHANNEL_MEMBER_REMOVED);
    }
}
