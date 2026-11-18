package com.korur.oba.apps.kazan.chat.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.dto.projection.ChatReactionSummaryProjection;
import com.korur.oba.apps.kazan.chat.dto.projection.ChatThreadSummaryProjection;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatLinkedTicketResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMemberResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessagePageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatReactionSummaryResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatReplyPreviewResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatThreadSummaryResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatTicketRefResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatUserResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatMessageMention;
import com.korur.oba.apps.kazan.chat.entity.ChatMessageTicketRef;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.mapper.TicketEmbeddedMapper;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;

/**
 * Maps chat messages, their mentions and ticket references between requests,
 * entities and responses.
 *
 * Relations (organization, thread, author, mention rows, ticket snapshots) are
 * set by {@link ChatMessageMapperHelper}; the per-reader parts of a response (canOpen,
 * linked keys, mentionsMe, repliesToMe, reactions, thread summary, daily update, users) are
 * computed there for a whole page and passed in as {@link ChatMessageParts}.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        uses = { TicketEmbeddedMapper.class })
public interface ChatMessageMapper {

    /**
     * The parts of a message response that depend on the reader or on other
     * rows than the message itself, computed by {@link ChatMessageMapperHelper}.
     *
     * @param clientNonce      nonce of the send that created the message; null outside that response
     * @param replyTo          preview of the quoted message; null when the message is no reply
     * @param mentionedUserIds users mentioned directly, in order of first mention
     * @param mentionsMe       true when the message mentions the reader
     * @param repliesToMe      true when the message, by someone else, replies to a live message of the reader
     * @param ticketRefs       ticket references with the reader's canOpen
     * @param linkedTickets    typed ticket keys the reader can open
     * @param attachments      attached files
     * @param reactions        reactions in first-reaction order
     * @param thread           summary of the thread started on the message; null when none
     * @param dailyUpdate      the daily update the message stands for; null otherwise or when not visible
     * @param pullRequestShare the pull request share the message stands for; null otherwise or when deleted
     * @param users            every user the message references, by ID
     */
    record ChatMessageParts(String clientNonce,
                            ChatReplyPreviewResponse replyTo,
                            List<UUID> mentionedUserIds,
                            boolean mentionsMe,
                            boolean repliesToMe,
                            List<ChatTicketRefResponse> ticketRefs,
                            List<ChatLinkedTicketResponse> linkedTickets,
                            List<AttachmentResponse> attachments,
                            List<ChatReactionSummaryResponse> reactions,
                            ChatThreadSummaryResponse thread,
                            DailyUpdateResponse dailyUpdate,
                            PullRequestShareResponse pullRequestShare,
                            Map<UUID, ChatUserResponse> users) {
    }

    // -------------Entities-----------//

    /**
     * Maps a post to a new message without relations: its channel, its quoted
     * message and the daily update or pull request share it stands for. A
     * missing text becomes empty, as an attachment-only message or a daily
     * update or share entry stores it.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "channelId", source = "channelId")
    @Mapping(target = "dailyUpdateId", source = "dailyUpdateId")
    @Mapping(target = "pullRequestShareId", source = "pullRequestShareId")
    @Mapping(target = "mentionsEveryone", ignore = true)
    @Mapping(target = "mentionsHere", ignore = true)
    @Mapping(target = "editedAt", ignore = true)
    @Mapping(target = "pinnedAt", ignore = true)
    @Mapping(target = "pinnedBy", ignore = true)
    @Mapping(target = "thread", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "mentions", ignore = true)
    @Mapping(target = "ticketRefs", ignore = true)
    @Mapping(target = "content", source = "content", qualifiedByName = "nullToEmpty")
    @Mapping(target = "replyToMessageId", source = "replyToMessageId")
    @Mapping(target = "clientNonce", source = "clientNonce")
    ChatMessage toEntity(ChatMessageCreateRequest request);

    /**
     * Replaces the text of a message. Mentions and ticket references are
     * applied by {@link ChatMessageMapperHelper#setUpdateRelations}; editedAt is set by
     * the service.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "channelId", ignore = true)
    @Mapping(target = "dailyUpdateId", ignore = true)
    @Mapping(target = "pullRequestShareId", ignore = true)
    @Mapping(target = "replyToMessageId", ignore = true)
    @Mapping(target = "mentionsEveryone", ignore = true)
    @Mapping(target = "mentionsHere", ignore = true)
    @Mapping(target = "editedAt", ignore = true)
    @Mapping(target = "pinnedAt", ignore = true)
    @Mapping(target = "pinnedBy", ignore = true)
    @Mapping(target = "thread", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "mentions", ignore = true)
    @Mapping(target = "ticketRefs", ignore = true)
    @Mapping(target = "content", source = "content", qualifiedByName = "nullToEmpty")
    void updateEntity(@MappingTarget ChatMessage message, ChatMessageUpdateRequest request);

    /**
     * Builds a mention row, not yet attached to a message.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "viaHere", source = "viaHere")
    ChatMessageMention toMentionEntity(UUID userId, boolean viaHere);

    /**
     * Builds a ticket reference with a snapshot of the ticket's key, title and
     * board, not yet attached to a message.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "boardId", source = "ticket.board.id")
    @Mapping(target = "ticketKey", source = "ticket.key")
    @Mapping(target = "ticketTitle", source = "ticket.title")
    @Mapping(target = "position", source = "position")
    ChatMessageTicketRef toTicketRefEntity(Ticket ticket, int position);

    /**
     * Refreshes a kept ticket reference on an edit: the snapshot is taken
     * again, because the edit re-resolves every reference with the author's access.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "boardId", source = "ticket.board.id")
    @Mapping(target = "ticketKey", source = "ticket.key")
    @Mapping(target = "ticketTitle", source = "ticket.title")
    @Mapping(target = "position", source = "position")
    void updateTicketRefEntity(@MappingTarget ChatMessageTicketRef ticketRef, Ticket ticket, int position);

    // -------------Responses-----------//

    /**
     * Maps a live message with its computed parts.
     */
    @Mapping(target = "id", source = "message.id")
    @Mapping(target = "organizationId", source = "message.organizationId")
    @Mapping(target = "channelId", source = "message.channelId")
    @Mapping(target = "threadId", source = "message.thread.id")
    @Mapping(target = "dailyUpdateId", source = "message.dailyUpdateId")
    @Mapping(target = "pullRequestShareId", source = "message.pullRequestShareId")
    @Mapping(target = "deleted", source = "message.deleted")
    @Mapping(target = "author", source = "message.author")
    @Mapping(target = "content", source = "message.content")
    @Mapping(target = "createdAt", source = "message.createdAt")
    @Mapping(target = "editedAt", source = "message.editedAt")
    @Mapping(target = "pinnedAt", source = "message.pinnedAt")
    @Mapping(target = "clientNonce", source = "parts.clientNonce")
    @Mapping(target = "replyTo", source = "parts.replyTo")
    @Mapping(target = "mentionedUserIds", source = "parts.mentionedUserIds")
    @Mapping(target = "mentionsEveryone", source = "message.mentionsEveryone")
    @Mapping(target = "mentionsHere", source = "message.mentionsHere")
    @Mapping(target = "mentionsMe", source = "parts.mentionsMe")
    @Mapping(target = "repliesToMe", source = "parts.repliesToMe")
    @Mapping(target = "ticketRefs", source = "parts.ticketRefs")
    @Mapping(target = "linkedTickets", source = "parts.linkedTickets")
    @Mapping(target = "attachments", source = "parts.attachments")
    @Mapping(target = "reactions", source = "parts.reactions")
    @Mapping(target = "thread", source = "parts.thread")
    @Mapping(target = "dailyUpdate", source = "parts.dailyUpdate")
    @Mapping(target = "pullRequestShare", source = "parts.pullRequestShare")
    @Mapping(target = "users", source = "parts.users")
    @Mapping(target = "version", source = "message.updatedAt")
    ChatMessageResponse toResponse(ChatMessage message, ChatMessageParts parts);

    /**
     * Maps the placeholder of a deleted thread root: no author, text, mentions,
     * reactions or files, only the thread summary and its users, so nothing of
     * the deleted message is returned. A thread root is always a channel
     * message, so the placeholder has no thread ID.
     *
     * @param messageId      ID of the deleted root
     * @param organizationId organization of the root
     * @param channelId      channel of the root; null when the root row is not at hand
     * @param createdAt      creation time of the root, its place in the channel; null when unknown
     * @param version        last change of the root's row; null when the row is not at hand
     * @param parts          the thread summary and its users; the lists empty
     */
    @Mapping(target = "id", source = "messageId")
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "channelId", source = "channelId")
    @Mapping(target = "threadId", ignore = true)
    @Mapping(target = "dailyUpdateId", ignore = true)
    @Mapping(target = "pullRequestShareId", ignore = true)
    @Mapping(target = "deleted", constant = "true")
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "content", constant = ChatConstant.EMPTY_CONTENT)
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "editedAt", ignore = true)
    @Mapping(target = "pinnedAt", ignore = true)
    @Mapping(target = "clientNonce", ignore = true)
    @Mapping(target = "replyTo", ignore = true)
    @Mapping(target = "mentionedUserIds", source = "parts.mentionedUserIds")
    @Mapping(target = "mentionsEveryone", constant = "false")
    @Mapping(target = "mentionsHere", constant = "false")
    @Mapping(target = "mentionsMe", constant = "false")
    @Mapping(target = "repliesToMe", constant = "false")
    @Mapping(target = "ticketRefs", source = "parts.ticketRefs")
    @Mapping(target = "linkedTickets", source = "parts.linkedTickets")
    @Mapping(target = "attachments", source = "parts.attachments")
    @Mapping(target = "reactions", source = "parts.reactions")
    @Mapping(target = "thread", source = "parts.thread")
    @Mapping(target = "dailyUpdate", ignore = true)
    @Mapping(target = "pullRequestShare", ignore = true)
    @Mapping(target = "users", source = "parts.users")
    @Mapping(target = "version", source = "version")
    ChatMessageResponse toPlaceholderResponse(UUID messageId, UUID organizationId, UUID channelId, Instant createdAt,
                                              Instant version, ChatMessageParts parts);

    /**
     * Maps a ticket reference with the reader's canOpen flag.
     */
    @Mapping(target = "ticketId", source = "ticketRef.ticketId")
    @Mapping(target = "boardId", source = "ticketRef.boardId")
    @Mapping(target = "ticketKey", source = "ticketRef.ticketKey")
    @Mapping(target = "ticketTitle", source = "ticketRef.ticketTitle")
    @Mapping(target = "canOpen", source = "canOpen")
    ChatTicketRefResponse toTicketRefResponse(ChatMessageTicketRef ticketRef, boolean canOpen);

    /**
     * Maps a typed ticket key the reader can open, keeping the key as typed
     * so the client finds it in the text.
     */
    @Mapping(target = "ticketKey", source = "ticketKey")
    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "boardId", source = "ticket.board.id")
    ChatLinkedTicketResponse toLinkedTicketResponse(String ticketKey, Ticket ticket);

    /**
     * Maps the reactions of one emoji on one message.
     */
    @Mapping(target = "emoji", source = "emoji")
    @Mapping(target = "count", source = "reactionCount")
    @Mapping(target = "reactedByMe", source = "reactedByMe")
    @Mapping(target = "userIds", source = "userIds", qualifiedByName = "uuidArrayToList")
    ChatReactionSummaryResponse toReactionSummaryResponse(ChatReactionSummaryProjection reaction);

    /**
     * Maps the reactions of one emoji on one message for no particular
     * reader, as a realtime payload carries them: reactedByMe is false and
     * each client sets its own.
     */
    @Mapping(target = "emoji", source = "emoji")
    @Mapping(target = "count", source = "reactionCount")
    @Mapping(target = "reactedByMe", constant = "false")
    @Mapping(target = "userIds", source = "userIds", qualifiedByName = "uuidArrayToList")
    ChatReactionSummaryResponse toNeutralReactionSummaryResponse(ChatReactionSummaryProjection reaction);

    /**
     * Maps the summary of the thread started on a message.
     */
    @Mapping(target = "threadId", source = "threadId")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "messageCount", source = "messageCount")
    @Mapping(target = "lastMessageAt", source = "lastMessageAt")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "memberPreviewIds", source = "memberPreviewIds", qualifiedByName = "uuidArrayToList")
    @Mapping(target = "memberCount", source = "memberCount")
    ChatThreadSummaryResponse toThreadSummaryResponse(ChatThreadSummaryProjection summary);

    /**
     * Maps the preview of a quoted message.
     *
     * @param messageId   ID of the quoted message
     * @param deleted     true when it was deleted
     * @param authorId    its author; null when deleted
     * @param snippet     its rendered start; empty when deleted
     * @param attachments its files; empty when deleted
     */
    @Mapping(target = "messageId", source = "messageId")
    @Mapping(target = "deleted", source = "deleted")
    @Mapping(target = "authorId", source = "authorId")
    @Mapping(target = "snippet", source = "snippet")
    @Mapping(target = "attachments", source = "attachments")
    ChatReplyPreviewResponse toReplyPreviewResponse(UUID messageId, boolean deleted, UUID authorId, String snippet,
                                                    List<AttachmentResponse> attachments);

    /**
     * Maps a user loaded as an entity, such as an author.
     */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "firstName", source = "user.profile.firstName")
    @Mapping(target = "lastName", source = "user.profile.lastName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "member", source = "member")
    ChatUserResponse toUserResponse(User user, boolean member);

    /**
     * Maps an active member of the organization.
     */
    @Mapping(target = "userId", source = "summary.userId")
    @Mapping(target = "firstName", source = "summary.firstName")
    @Mapping(target = "lastName", source = "summary.lastName")
    @Mapping(target = "email", source = "summary.email")
    @Mapping(target = "member", constant = "true")
    ChatUserResponse toMemberUserResponse(OrganizationMemberSummaryProjection summary);

    /**
     * Maps an entry of the channel member list with the member's online state and whether the
     * member may open Kazan in the organization.
     */
    @Mapping(target = "userId", source = "member.userId")
    @Mapping(target = "firstName", source = "member.firstName")
    @Mapping(target = "lastName", source = "member.lastName")
    @Mapping(target = "email", source = "member.email")
    @Mapping(target = "role", source = "member.role", qualifiedByName = "organizationRole")
    @Mapping(target = "online", source = "online")
    @Mapping(target = "kazanAccess", source = "kazanAccess")
    ChatMemberResponse toMemberResponse(OrganizationMemberSummaryProjection member, boolean online,
                                        boolean kazanAccess);

    /**
     * Maps a user whose account no longer exists: only the ID is known, and
     * the client renders the user as an unknown member.
     */
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "member", constant = "false")
    ChatUserResponse toUnknownUserResponse(UUID userId);

    /**
     * Maps a keyset page of message responses.
     */
    @Mapping(target = "items", source = "items")
    @Mapping(target = "hasOlder", source = "hasOlder")
    @Mapping(target = "hasNewer", source = "hasNewer")
    ChatMessagePageResponse toPageResponse(List<ChatMessageResponse> items, boolean hasOlder, boolean hasNewer);

    /**
     * Turns a missing text into the empty text the column stores.
     */
    @Named("nullToEmpty")
    default String nullToEmpty(String value) {
        return value == null ? ChatConstant.EMPTY_CONTENT : value;
    }

    /**
     * Turns the role the member projection reads as text back into the enum
     * it was stored from.
     */
    @Named("organizationRole")
    default OrganizationMemberRole organizationRole(String role) {
        return OrganizationMemberRole.valueOf(role);
    }

    /**
     * Turns a database UUID array into a list; a missing array is an empty list.
     */
    @Named("uuidArrayToList")
    default List<UUID> uuidArrayToList(UUID[] ids) {
        return ids == null ? List.of() : List.of(ids);
    }
}
