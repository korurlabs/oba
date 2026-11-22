package com.korur.oba.apps.kazan.chat.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.text.BreakIterator;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageQueryRequest;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kazan.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kazan.chat.helper.ChatTokenParser.ChatTokens;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kazan.pullrequest.service.PullRequestShareQueryService;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.dojo.exception.enums.ExceptionStateError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Validator for chat channels, messages, reactions, pins, threads, daily
 * update and pull request share entries and page cursors: lookups scoped to an organization,
 * authorship and role rules, channel field rules, and the limits the client
 * enforces too.
 *
 * Every refusal here answers a request the client got wrong or may not make,
 * an expected 4xx and no server fault, so it is logged at warn, never at
 * error, and error alerts keep pointing at real failures.
 *
 * Every lookup is scoped to the request's organization, and a row of another
 * organization is reported as not found, never as forbidden, so a message or
 * thread ID reveals nothing about other organizations. The request DTOs carry
 * the size limits as Bean Validation annotations as well; the checks here also
 * count what only the server can count, such as the tokens outside code, and
 * guard callers that build a request without the controller, such as the daily
 * update service.
 */
@Component
@RequiredArgsConstructor
public class ChatValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatValidator.class);

    private final ChatMessageRepository chatMessageRepository;
    private final ChatThreadRepository chatThreadRepository;
    private final DailyUpdateQueryService dailyUpdateQueryService;
    private final PullRequestShareQueryService pullRequestShareQueryService;
    private final AppAccessValidator appAccessValidator;
    private final LoggingService loggingService;

    /**
     * Ensures a live message exists in the organization and returns it.
     *
     * @param organizationId the request's organization
     * @param messageId      ID of the message
     * @return the message
     * @throws NotFoundException NOT_CHAT_MESSAGE_FOUND when the message is absent, deleted or of another organization
     */
    public ChatMessage ensureMessageExists(UUID organizationId, UUID messageId) {
        return chatMessageRepository.findByIdAndOrganizationId(messageId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            ChatExceptionLogConstant.CHAT_MESSAGE_NOT_FOUND, messageId, organizationId);
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_CHAT_MESSAGE_FOUND,
                            Map.of(ChatConstant.FIELD_MESSAGE_ID, messageId));
                });
    }

    /**
     * Ensures a thread exists in the organization and returns it.
     *
     * @param organizationId the request's organization
     * @param threadId       ID of the thread
     * @return the thread
     * @throws NotFoundException NOT_CHAT_THREAD_FOUND when the thread is absent or of another organization
     */
    public ChatThread ensureThreadExists(UUID organizationId, UUID threadId) {
        return chatThreadRepository.findByIdAndOrganizationId(threadId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            ChatExceptionLogConstant.CHAT_THREAD_NOT_FOUND, threadId, organizationId);
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_CHAT_THREAD_FOUND,
                            Map.of(ChatConstant.FIELD_THREAD_ID, threadId));
                });
    }

    /**
     * Ensures a thread belongs to the channel a request names, so a thread ID
     * never reaches into another channel.
     *
     * @param thread    the thread, from ensureThreadExists
     * @param channelId the channel of the request
     * @throws NotFoundException NOT_CHAT_THREAD_FOUND when the thread is in another channel
     */
    public void ensureThreadInChannel(ChatThread thread, UUID channelId) {
        if (!thread.getChannelId().equals(channelId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_THREAD_OUTSIDE_CHANNEL, thread.getId(), channelId);
            throw new NotFoundException(
                    ExceptionDomainKazanError.NOT_CHAT_THREAD_FOUND,
                    Map.of(ChatConstant.FIELD_THREAD_ID, thread.getId()));
        }
    }

    /**
     * Ensures a message stays within the send limits: text after trimming, at
     * least one file, or a daily update or pull request share the message
     * stands for; at most
     * MAX_CONTENT_LENGTH characters, tokens included; at most MAX_FILES files,
     * MAX_USER_MENTIONS distinct users and MAX_TICKET_REFS distinct tickets.
     * Tokens inside code are not counted, because they are no mentions or
     * references.
     *
     * @param userId        the sender, for the log
     * @param content       message text, nullable
     * @param fileCount     number of files sent with the message; 0 for an edit
     * @param tokens        the tokens of content, from {@link com.korur.oba.apps.kazan.chat.helper.ChatTokenParser#parse}
     * @param dailyUpdateId daily update the message stands for, whose card is its content; null otherwise
     * @param pullRequestShareId pull request share the message stands for, whose card is its content; null otherwise
     * @throws ValidationException VAL_CHAT_MESSAGE_INVALID naming the first broken limit and its value
     */
    public void ensureWithinLimits(UUID userId, String content, int fileCount, ChatTokens tokens,
                                   UUID dailyUpdateId, UUID pullRequestShareId) {
        if ((content == null || content.isBlank()) && fileCount == 0 && dailyUpdateId == null
                && pullRequestShareId == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, ChatExceptionLogConstant.CHAT_MESSAGE_EMPTY, userId);
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_CHAT_MESSAGE_INVALID,
                    Map.of(ChatConstant.FIELD_CONTENT, ChatConstant.MAX_CONTENT_LENGTH));
        }
        if (content != null && content.length() > ChatConstant.MAX_CONTENT_LENGTH) {
            throw limitExceeded(ChatConstant.FIELD_CONTENT, ChatConstant.MAX_CONTENT_LENGTH, content.length());
        }
        if (fileCount > ChatConstant.MAX_FILES) {
            throw limitExceeded(ChatConstant.FIELD_FILES, ChatConstant.MAX_FILES, fileCount);
        }
        if (tokens.userIds().size() > ChatConstant.MAX_USER_MENTIONS) {
            throw limitExceeded(ChatConstant.FIELD_MENTIONS, ChatConstant.MAX_USER_MENTIONS, tokens.userIds().size());
        }
        if (tokens.ticketIds().size() > ChatConstant.MAX_TICKET_REFS) {
            throw limitExceeded(ChatConstant.FIELD_TICKET_REFS, ChatConstant.MAX_TICKET_REFS,
                    tokens.ticketIds().size());
        }
    }

    /**
     * Ensures every mentioned user can read the conversation: an active
     * member of the organization for a public channel, a member of the
     * channel for a private one, and in both cases a member who may open
     * Kazan in the organization.
     *
     * @param organizationId  the request's organization
     * @param mentionedIds    users mentioned directly
     * @param activeMemberIds IDs of the users who can read the conversation
     * @throws ValidationException VAL_CHAT_MENTION_NOT_MEMBER naming the first user who cannot read it,
     *         or APP_VAL_002 naming the first one who may not open Kazan
     */
    public void ensureMentionsAreMembers(UUID organizationId, Collection<UUID> mentionedIds,
                                         Collection<UUID> activeMemberIds) {
        Set<UUID> members = new HashSet<>(activeMemberIds);
        for (UUID mentionedId : mentionedIds) {
            if (!members.contains(mentionedId)) {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                        ChatExceptionLogConstant.CHAT_MENTION_NOT_MEMBER, mentionedId, organizationId);
                throw new ValidationException(
                        ExceptionDomainKazanError.VAL_CHAT_MENTION_NOT_MEMBER,
                        Map.of(ChatConstant.FIELD_USER_ID, mentionedId));
            }
        }
        appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.KAZAN, mentionedIds);
    }

    /**
     * Ensures every referenced ticket was found among the tickets the author
     * can reach. The tickets come from
     * {@link com.korur.oba.apps.kazan.ticket.service.TicketQueryService#getAccessibleTickets},
     * which leaves out deleted tickets and tickets on boards the author is not
     * a member of; both are reported as not found, so a foreign ticket is not
     * revealed.
     *
     * @param requestedTicketIds tickets referenced by the message
     * @param foundTickets       tickets returned for those IDs
     * @throws NotFoundException NOT_TICKET_FOUND naming the first missing ticket
     */
    public void ensureTicketsAccessible(Collection<UUID> requestedTicketIds, Collection<Ticket> foundTickets) {
        Set<UUID> foundIds = new HashSet<>();
        for (Ticket ticket : foundTickets) {
            foundIds.add(ticket.getId());
        }
        List<UUID> missingIds = requestedTicketIds.stream()
                .filter(ticketId -> !foundIds.contains(ticketId))
                .toList();
        if (!missingIds.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_TICKET_REFS_NOT_ACCESSIBLE, missingIds);
            throw new NotFoundException(
                    ExceptionDomainKazanError.NOT_TICKET_FOUND,
                    Map.of(ChatConstant.FIELD_TICKET_ID, missingIds.getFirst()));
        }
    }

    /**
     * Ensures a reply quotes a message of the same conversation: the channel
     * for a channel message, the same thread for a thread message.
     *
     * @param target   the quoted message, from ensureMessageExists
     * @param threadId thread of the new message; null in the channel
     * @throws ValidationException VAL_CHAT_REPLY_OUTSIDE_CONVERSATION when the target is elsewhere
     */
    public void ensureReplyInConversation(ChatMessage target, UUID threadId) {
        UUID targetThreadId = target.getThread() == null ? null : target.getThread().getId();
        if (!Objects.equals(targetThreadId, threadId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_REPLY_OUTSIDE_CONVERSATION, target.getId(), threadId);
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_CHAT_REPLY_OUTSIDE_CONVERSATION,
                    Map.of(ChatConstant.FIELD_REPLY_TO_MESSAGE_ID, target.getId()));
        }
    }

    /**
     * Ensures the user wrote the message; only the author may edit it, also
     * against an owner or admin, so nobody puts words in another's name.
     *
     * @param message the message
     * @param user    the current user
     * @throws ForbiddenException SEC_CANNOT_MODIFY_OTHERS_MESSAGE when the user is not the author
     */
    public void ensureAuthor(ChatMessage message, User user) {
        if (!message.getAuthor().getId().equals(user.getId())) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_MESSAGE_NOT_AUTHOR, message.getId(), user.getId());
            throw new ForbiddenException(
                    ExceptionDomainKazanError.SEC_CANNOT_MODIFY_OTHERS_MESSAGE,
                    Map.of(ChatConstant.FIELD_MESSAGE_ID, message.getId()));
        }
    }

    /**
     * Ensures the user may delete the message: its author, or an owner or
     * admin of the organization, who moderate the channel.
     *
     * @param message the message
     * @param user    the current user
     * @param role    the user's role in the organization; null when the user is no member
     * @throws ForbiddenException SEC_CANNOT_DELETE_OTHERS_MESSAGE otherwise
     */
    public void ensureCanDelete(ChatMessage message, User user, OrganizationMemberRole role) {
        if (!message.getAuthor().getId().equals(user.getId()) && !isOwnerOrAdmin(role)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_DELETE_NOT_ALLOWED, message.getId(), user.getId(), role);
            throw new ForbiddenException(
                    ExceptionDomainKazanError.SEC_CANNOT_DELETE_OTHERS_MESSAGE,
                    Map.of(ChatConstant.FIELD_MESSAGE_ID, message.getId()));
        }
    }

    /**
     * Ensures the message is no daily update message, which changes only
     * through its daily update, so the update and its message never diverge.
     *
     * @param message the message
     * @throws ValidationException VAL_CHAT_DAILY_UPDATE_MESSAGE_READ_ONLY for a message with a daily update
     */
    public void ensureNotDailyUpdate(ChatMessage message) {
        if (message.getDailyUpdateId() != null) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_DAILY_UPDATE_MESSAGE_READ_ONLY, message.getId());
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_CHAT_DAILY_UPDATE_MESSAGE_READ_ONLY,
                    Map.of(ChatConstant.FIELD_MESSAGE_ID, message.getId()));
        }
    }

    /**
     * Ensures the message is no pull request share entry, which changes only
     * through its share's own edit and delete, so the share and its card
     * never diverge. The error names the share, so a client can tell this
     * refusal from any other invalid edit.
     *
     * @param message the message
     * @throws ValidationException GEN_VAL_001 naming pullRequestShareId for a share entry
     */
    public void ensureNotPullRequestShare(ChatMessage message) {
        if (message.getPullRequestShareId() != null) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_PULL_REQUEST_SHARE_MESSAGE_READ_ONLY, message.getId(),
                    message.getPullRequestShareId());
            throw new ValidationException(
                    ExceptionValidationError.VALIDATION,
                    Map.of(ChatConstant.FIELD_PULL_REQUEST_SHARE_ID, message.getPullRequestShareId()));
        }
    }

    /**
     * Ensures the user may pin or unpin the message: its author, or an owner
     * or admin of the organization.
     *
     * @param message the message
     * @param user    the current user
     * @param role    the user's role in the organization
     * @throws ForbiddenException SEC_CANNOT_PIN_MESSAGE otherwise
     */
    public void ensureCanPin(ChatMessage message, User user, OrganizationMemberRole role) {
        if (!message.getAuthor().getId().equals(user.getId()) && !isOwnerOrAdmin(role)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_PIN_NOT_ALLOWED, message.getId(), user.getId());
            throw new ForbiddenException(
                    ExceptionDomainKazanError.SEC_CANNOT_PIN_MESSAGE,
                    Map.of(ChatConstant.FIELD_MESSAGE_ID, message.getId()));
        }
    }

    /**
     * Ensures a channel, its threads included, has room for one more pin.
     *
     * @param channelId the channel of the message to pin
     * @throws ValidationException VAL_CHAT_PIN_LIMIT_REACHED when MAX_PINS live messages are pinned
     */
    public void ensurePinLimitNotReached(UUID channelId) {
        if (chatMessageRepository.countPinnedMessages(channelId) >= ChatConstant.MAX_PINS) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_PIN_LIMIT_REACHED, channelId, ChatConstant.MAX_PINS);
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_CHAT_PIN_LIMIT_REACHED,
                    Map.of(ChatConstant.FIELD_PINS, ChatConstant.MAX_PINS));
        }
    }

    /**
     * Ensures a page request names at most one of before, after and around.
     *
     * @param request the page request
     * @throws ValidationException VAL_CHAT_CURSOR_CONFLICT when two or more cursors are set
     */
    public void ensureSingleCursor(ChatMessageQueryRequest request) {
        int cursors = (request.getBefore() == null ? 0 : 1)
                + (request.getAfter() == null ? 0 : 1)
                + (request.getAround() == null ? 0 : 1);
        if (cursors > 1) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, ChatExceptionLogConstant.CHAT_CURSOR_CONFLICT,
                    request.getBefore(), request.getAfter(), request.getAround());
            Map<String, Object> cursorValues = new HashMap<>();
            cursorValues.put(ChatConstant.FIELD_CURSOR, cursors);
            throw new ValidationException(ExceptionDomainKazanError.VAL_CHAT_CURSOR_CONFLICT, cursorValues);
        }
    }

    /**
     * Returns the page size a read of messages, pins or threads asks for: the
     * given limit, or the default when the request names none. Request
     * validation already rejects a limit outside the range, and the check
     * repeats here because the size and its probe row become the row count of
     * a query: a caller that skips request validation must get the same
     * rejection, never a negative or overflowing row count. Callers compute
     * with the returned value only.
     *
     * @param limit       the limit of the request, or null for the default
     * @param defaultSize the page size when the request names no limit
     * @return the validated page size
     * @throws ValidationException {@link ExceptionValidationError#VALIDATION} naming the largest allowed limit
     */
    public int ensurePageSize(Integer limit, int defaultSize) {
        if (limit == null) {
            return defaultSize;
        }
        if (limit < ChatConstant.MIN_PAGE_SIZE || limit > ChatConstant.MAX_PAGE_SIZE) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, ChatExceptionLogConstant.CHAT_PAGE_SIZE_OUT_OF_RANGE,
                    limit, ChatConstant.MIN_PAGE_SIZE, ChatConstant.MAX_PAGE_SIZE);
            throw new ValidationException(ExceptionValidationError.VALIDATION,
                    Map.of(ChatConstant.FIELD_LIMIT, ChatConstant.MAX_PAGE_SIZE));
        }
        return limit;
    }

    /**
     * Ensures an emoji is exactly one grapheme of at most MAX_EMOJI_LENGTH
     * UTF-16 units. One grapheme keeps a reaction to one symbol, also for
     * emoji built from several code points such as a family or a flag.
     *
     * @param messageId the message reacted to, for the log
     * @param emoji     the emoji
     * @throws ValidationException VAL_CHAT_REACTION_INVALID otherwise
     */
    public void ensureEmojiValid(UUID messageId, String emoji) {
        int length = emoji == null ? 0 : emoji.length();
        if (length == 0 || length > ChatConstant.MAX_EMOJI_LENGTH || countGraphemes(emoji) != 1) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_REACTION_INVALID_EMOJI, messageId, length);
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_CHAT_REACTION_INVALID,
                    Map.of(ChatConstant.FIELD_EMOJI, ChatConstant.MAX_EMOJI_LENGTH));
        }
    }

    /**
     * Ensures a new emoji fits on the message: a message holds at most
     * MAX_REACTIONS_PER_MESSAGE distinct emoji, and another reaction with an
     * emoji already present always fits.
     *
     * @param messageId     the message reacted to
     * @param emojiPresent  true when the message already has a reaction with this emoji
     * @param distinctEmoji number of distinct emoji on the message
     * @throws ValidationException VAL_CHAT_REACTION_INVALID when a new emoji would exceed the limit
     */
    public void ensureReactionLimitNotReached(UUID messageId, boolean emojiPresent, long distinctEmoji) {
        if (!emojiPresent && distinctEmoji >= ChatConstant.MAX_REACTIONS_PER_MESSAGE) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_REACTION_LIMIT_REACHED, messageId,
                    ChatConstant.MAX_REACTIONS_PER_MESSAGE);
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_CHAT_REACTION_INVALID,
                    Map.of(ChatConstant.FIELD_REACTIONS, ChatConstant.MAX_REACTIONS_PER_MESSAGE));
        }
    }

    /**
     * Ensures a message can host a thread: a channel message, so threads do
     * not nest. A deleted root is already refused by ensureMessageExists.
     *
     * @param root the root message
     * @throws ValidationException VAL_CHAT_THREAD_INVALID for a thread message
     */
    public void ensureThreadRoot(ChatMessage root) {
        if (root.getThread() != null) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_THREAD_ROOT_INVALID, root.getId());
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_CHAT_THREAD_INVALID,
                    Map.of(ChatConstant.FIELD_ROOT_MESSAGE_ID, root.getId()));
        }
    }

    /**
     * Ensures the root message has no thread yet. The 409 body names the
     * existing thread, so the client opens it instead.
     *
     * @param rootMessageId the root message
     * @throws ConflictException STA_CHAT_THREAD_EXISTS with the existing thread's ID
     */
    public void ensureNoThreadOn(UUID rootMessageId) {
        chatThreadRepository.findByRootMessageId(rootMessageId).ifPresent(existing -> {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_THREAD_EXISTS, rootMessageId, existing.getId());
            throw new ConflictException(
                    ExceptionDomainKazanError.STA_CHAT_THREAD_EXISTS,
                    Map.of(ChatConstant.FIELD_THREAD_ID, existing.getId()));
        });
    }

    /**
     * Ensures an auto-archive window is one of AUTO_ARCHIVE_MINUTES_OPTIONS,
     * the set the database check allows.
     *
     * @param threadId the thread, for the log; null while creating
     * @param minutes  the window
     * @throws ValidationException VAL_CHAT_THREAD_INVALID otherwise
     */
    public void ensureAutoArchiveMinutesValid(UUID threadId, Integer minutes) {
        if (minutes == null || !ChatConstant.AUTO_ARCHIVE_MINUTES_OPTIONS.contains(minutes)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_THREAD_AUTO_ARCHIVE_INVALID, threadId, minutes);
            Map<String, Object> values = new HashMap<>();
            values.put(ChatConstant.FIELD_AUTO_ARCHIVE_MINUTES, minutes);
            throw new ValidationException(ExceptionDomainKazanError.VAL_CHAT_THREAD_INVALID, values);
        }
    }

    /**
     * Ensures a thread accepts replies and edits: a CLOSED thread is
     * read-only until it is reopened. Deleting, reacting and pinning stay
     * allowed and do not call this.
     *
     * @param thread the thread
     * @throws ConflictException STA_CHAT_THREAD_CLOSED when the thread is closed
     */
    public void ensureThreadOpen(ChatThread thread) {
        if (thread.getStatus() == ChatThreadStatus.CLOSED) {
            throw threadClosed(thread.getId());
        }
    }

    /**
     * Ensures a reply was recorded on its thread. The counter update finds no
     * row when the thread was closed after ensureThreadOpen passed, and the
     * reply must then fail like one sent after the close.
     *
     * @param threadId    the thread
     * @param updatedRows rows changed by {@link com.korur.oba.apps.kazan.chat.repository.ChatThreadRepository#incrementMessageCount}
     * @throws ConflictException STA_CHAT_THREAD_CLOSED when no row was changed
     */
    public void ensureReplyRecorded(UUID threadId, int updatedRows) {
        if (updatedRows == 0) {
            throw threadClosed(threadId);
        }
    }

    /**
     * Ensures the user may rename the thread, change its auto-archive
     * window and change its status: its creator, or an owner or admin of the organization.
     *
     * @param thread the thread
     * @param user   the current user
     * @param role   the user's role in the organization
     * @throws ForbiddenException SEC_CANNOT_MANAGE_THREAD otherwise
     */
    public void ensureCanManageThread(ChatThread thread, User user, OrganizationMemberRole role) {
        if (!canManageThread(thread, user.getId(), role)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_THREAD_MANAGE_NOT_ALLOWED, thread.getId(), user.getId());
            throw new ForbiddenException(
                    ExceptionDomainKazanError.SEC_CANNOT_MANAGE_THREAD,
                    Map.of(ChatConstant.FIELD_THREAD_ID, thread.getId()));
        }
    }

    /**
     * Tells whether a user may manage a thread, for the canManage flag of a
     * thread response and for ensureCanManageThread.
     *
     * @param thread the thread
     * @param userId the user
     * @param role   the user's role in the organization; null when the user is no member
     * @return true for the creator and for owners and admins
     */
    public boolean canManageThread(ChatThread thread, UUID userId, OrganizationMemberRole role) {
        return thread.getCreatedBy().equals(userId) || isOwnerOrAdmin(role);
    }

    /**
     * Ensures a channel's members are given only where they are taken: on
     * creating a private channel, and on switching a public channel to
     * private. Elsewhere the member list is managed through the member
     * endpoints, so a list sent with the update would be silently ignored.
     *
     * @param channel    the stored channel; null while creating
     * @param visibility the visibility the request asks for
     * @param memberIds  the members the request names; null or empty for none
     * @throws ValidationException VAL_CHAT_CHANNEL_MEMBERS_NOT_EXPECTED when members are given elsewhere
     */
    public void ensureChannelMembersExpected(ChatChannel channel, ChatChannelVisibility visibility,
                                             List<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }
        boolean becomesPrivate = visibility == ChatChannelVisibility.PRIVATE
                && (channel == null || channel.getVisibility() == ChatChannelVisibility.PUBLIC);
        if (!becomesPrivate) {
            UUID channelId = channel == null ? null : channel.getId();
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_CHANNEL_MEMBERS_NOT_EXPECTED, channelId, visibility);
            Map<String, Object> values = new HashMap<>();
            values.put(ChatConstant.FIELD_MEMBER_IDS, memberIds.size());
            throw new ValidationException(ExceptionDomainKazanError.VAL_CHAT_CHANNEL_MEMBERS_NOT_EXPECTED, values);
        }
    }

    /**
     * Ensures a channel is private before its member list is changed; a
     * public channel is read by every member of the organization and has no
     * list.
     *
     * @param channel the channel
     * @throws ValidationException VAL_CHAT_CHANNEL_NOT_PRIVATE for a public channel
     */
    public void ensureChannelPrivate(ChatChannel channel) {
        if (channel.getVisibility() != ChatChannelVisibility.PRIVATE) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_CHANNEL_NOT_PRIVATE, channel.getId());
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_CHAT_CHANNEL_NOT_PRIVATE,
                    Map.of(ChatConstant.FIELD_CHANNEL_ID, channel.getId()));
        }
    }

    /**
     * Ensures a purpose other than {@link ChatChannelPurpose#GENERAL} stays on
     * a public channel: such a channel is a fixed place every member of the
     * organization reads, such as the one receiving the daily update cards.
     * The database check chat_channels_purpose_ck holds the same rule.
     *
     * @param channelId  the channel; null while creating
     * @param visibility the visibility the request asks for
     * @param purpose    the purpose the request asks for
     * @throws ValidationException VAL_CHAT_CHANNEL_PURPOSE_PRIVATE for a private channel with such a purpose
     */
    public void ensureAutoPostChannelPublic(UUID channelId, ChatChannelVisibility visibility,
                                            ChatChannelPurpose purpose) {
        if (purpose != ChatChannelPurpose.GENERAL && visibility == ChatChannelVisibility.PRIVATE) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_CHANNEL_PURPOSE_PRIVATE, purpose, channelId);
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_CHAT_CHANNEL_PURPOSE_PRIVATE,
                    Map.of(ChatConstant.FIELD_PURPOSE, purpose.name()));
        }
    }

    /**
     * Ensures a channel asking for a purpose other than
     * {@link ChatChannelPurpose#GENERAL} does not take it from another
     * channel: only one live channel of an organization holds such a
     * purpose, and an admin gives it up explicitly by setting that channel to
     * GENERAL first, so posts and readers never move to a channel by
     * surprise. The channel that already holds the purpose may keep it.
     *
     * @param organizationId the request's organization, for the log
     * @param channelId      the channel; null while creating
     * @param purpose        the purpose the request asks for
     * @param holderId       the live channel holding the purpose; null when none does
     * @throws ConflictException STA_CHAT_PURPOSE_TAKEN when another channel holds the purpose
     */
    public void ensurePurposeFree(UUID organizationId, UUID channelId, ChatChannelPurpose purpose, UUID holderId) {
        if (holderId != null && !holderId.equals(channelId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_CHANNEL_PURPOSE_TAKEN, purpose, channelId, holderId, organizationId);
            throw new ConflictException(
                    ExceptionDomainKazanError.STA_CHAT_PURPOSE_TAKEN,
                    Map.of(ChatConstant.FIELD_PURPOSE, purpose.name()));
        }
    }

    /**
     * Ensures every user put on a channel's member list is an active member
     * of the organization.
     *
     * @param organizationId  the request's organization, for the log
     * @param userIds         the users to put on the list
     * @param activeMemberIds IDs of the organization's active members
     * @throws ValidationException VAL_CHAT_MENTION_NOT_MEMBER naming the first user who is no active member
     */
    public void ensureChannelMembersActive(UUID organizationId, Collection<UUID> userIds,
                                           Collection<UUID> activeMemberIds) {
        Set<UUID> members = new HashSet<>(activeMemberIds);
        for (UUID userId : userIds) {
            if (!members.contains(userId)) {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                        ChatExceptionLogConstant.CHAT_CHANNEL_MEMBER_NOT_ACTIVE, userId, organizationId);
                throw new ValidationException(
                        ExceptionDomainKazanError.VAL_CHAT_MENTION_NOT_MEMBER,
                        Map.of(ChatConstant.FIELD_USER_ID, userId));
            }
        }
    }

    /**
     * Ensures a message may stand for a daily update: the update is live and
     * belongs to the request's organization, the sender wrote it, the channel
     * is the daily updates channel, and the update has no chat entry yet, a
     * deleted one included. An update of another organization is reported as
     * not found, so its ID reveals nothing there. The unique key on
     * daily_update_id is the final guard against two entries posted at once.
     *
     * @param user           the sender
     * @param organizationId the request's organization
     * @param channel        the channel posted to, from
     *                       {@link com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService#verifyChannelAccess}
     * @param dailyUpdateId  the daily update
     * @throws NotFoundException   NOT_DAILY_UPDATE_FOUND when the update is absent, deleted or of another
     *                             organization
     * @throws ForbiddenException  SEC_CANNOT_POST_OTHERS_UPDATE when the sender is not its author
     * @throws ValidationException VAL_CHAT_DAILY_UPDATE_CHANNEL when the channel is not the daily updates channel
     * @throws ConflictException   STA_CHAT_DAILY_UPDATE_POSTED when the update already has an entry
     */
    public void ensureDailyUpdateEntry(User user, UUID organizationId, ChatChannel channel, UUID dailyUpdateId) {
        DailyUpdate dailyUpdate = dailyUpdateQueryService.findDailyUpdate(dailyUpdateId)
                .filter(found -> organizationId.equals(found.getOrganizationId()))
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            ChatExceptionLogConstant.CHAT_DAILY_UPDATE_NOT_FOUND, dailyUpdateId, organizationId);
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_DAILY_UPDATE_FOUND,
                            Map.of(ChatConstant.FIELD_DAILY_UPDATE_ID, dailyUpdateId));
                });
        if (!user.getId().equals(dailyUpdate.getAuthor().getId())) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_DAILY_UPDATE_NOT_AUTHOR, dailyUpdateId, user.getId());
            throw new ForbiddenException(
                    ExceptionDomainKazanError.SEC_CANNOT_POST_OTHERS_UPDATE,
                    Map.of(ChatConstant.FIELD_DAILY_UPDATE_ID, dailyUpdateId));
        }
        if (channel.getPurpose() != ChatChannelPurpose.DAILY_UPDATES) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_DAILY_UPDATE_CHANNEL_INVALID, dailyUpdateId, channel.getId(),
                    organizationId);
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_CHAT_DAILY_UPDATE_CHANNEL,
                    Map.of(ChatConstant.FIELD_CHANNEL_ID, channel.getId()));
        }
        if (chatMessageRepository.existsByDailyUpdateIdWithDeleted(dailyUpdateId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_DAILY_UPDATE_ALREADY_POSTED, dailyUpdateId);
            throw new ConflictException(
                    ExceptionDomainKazanError.STA_CHAT_DAILY_UPDATE_POSTED,
                    Map.of(ChatConstant.FIELD_DAILY_UPDATE_ID, dailyUpdateId));
        }
    }

    /**
     * Ensures a message may stand for a pull request share: the share is live
     * and belongs to the request's organization, the sender wrote it, the
     * channel is the pull requests channel, and the share has no chat entry
     * yet, a deleted one included. A share of another organization is
     * reported as not found, so its ID reveals nothing there. The unique key
     * on pull_request_share_id is the final guard against two entries posted
     * at once.
     *
     * @param user               the sender
     * @param organizationId     the request's organization
     * @param channel            the channel posted to, from
     *                           {@link com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService#verifyChannelAccess}
     * @param pullRequestShareId the pull request share
     * @return the share, whose reviewers become the entry's mentions
     * @throws NotFoundException   NOT_PULL_REQUEST_SHARE_FOUND when the share is absent, deleted or of another
     *                             organization
     * @throws ForbiddenException  SEC_CANNOT_EDIT_OTHERS_SHARE when the sender is not its author
     * @throws ValidationException GEN_VAL_001 naming the channel when it is not the pull requests channel
     * @throws ConflictException   GEN_STA_004 when the share already has an entry
     */
    public PullRequestShare ensurePullRequestShareEntry(User user, UUID organizationId, ChatChannel channel,
                                                        UUID pullRequestShareId) {
        PullRequestShare share = pullRequestShareQueryService.findShare(organizationId, pullRequestShareId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                            ChatExceptionLogConstant.CHAT_PULL_REQUEST_SHARE_NOT_FOUND, pullRequestShareId,
                            organizationId);
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_PULL_REQUEST_SHARE_FOUND,
                            Map.of(ChatConstant.FIELD_PULL_REQUEST_SHARE_ID, pullRequestShareId));
                });
        if (!user.getId().equals(share.getAuthor().getId())) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_PULL_REQUEST_SHARE_NOT_AUTHOR, pullRequestShareId, user.getId());
            throw new ForbiddenException(
                    ExceptionDomainKazanError.SEC_CANNOT_EDIT_OTHERS_SHARE,
                    Map.of(ChatConstant.FIELD_PULL_REQUEST_SHARE_ID, pullRequestShareId));
        }
        if (channel.getPurpose() != ChatChannelPurpose.PULL_REQUESTS) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_PULL_REQUEST_SHARE_CHANNEL_INVALID, pullRequestShareId,
                    channel.getId(), organizationId);
            throw new ValidationException(
                    ExceptionValidationError.VALIDATION,
                    Map.of(ChatConstant.FIELD_CHANNEL_ID, channel.getId()));
        }
        if (chatMessageRepository.existsByPullRequestShareIdWithDeleted(pullRequestShareId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    ChatExceptionLogConstant.CHAT_PULL_REQUEST_SHARE_ALREADY_POSTED, pullRequestShareId);
            throw new ConflictException(
                    ExceptionStateError.DUPLICATE_ENTITY,
                    Map.of(ChatConstant.FIELD_PULL_REQUEST_SHARE_ID, pullRequestShareId));
        }
        return share;
    }

    /**
     * Ensures a pull request share entry stands alone: no thread, no files and
     * no daily update. The entry is a card of the pull requests channel
     * itself, and the schema allows it neither in a thread nor beside a daily
     * update.
     *
     * @param pullRequestShareId the pull request share
     * @param threadId           the thread posted to; null for the channel
     * @param fileCount          number of files sent with the entry
     * @param dailyUpdateId      the daily update sent with the entry; null for none
     * @throws ValidationException GEN_VAL_001 naming the first field the entry may not carry
     */
    public void ensurePullRequestShareAlone(UUID pullRequestShareId, UUID threadId, int fileCount,
                                            UUID dailyUpdateId) {
        String field = null;
        Object value = null;
        if (threadId != null) {
            field = ChatConstant.FIELD_THREAD_ID;
            value = threadId;
        } else if (fileCount > 0) {
            field = ChatConstant.FIELD_FILES;
            value = fileCount;
        } else if (dailyUpdateId != null) {
            field = ChatConstant.FIELD_DAILY_UPDATE_ID;
            value = dailyUpdateId;
        }
        if (field == null) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, ChatExceptionLogConstant.CHAT_PULL_REQUEST_SHARE_NOT_ALONE,
                field, pullRequestShareId);
        throw new ValidationException(ExceptionValidationError.VALIDATION, Map.of(field, value));
    }

    /**
     * Logs and builds the error of a reply or edit in a closed thread.
     */
    private ConflictException threadClosed(UUID threadId) {
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, ChatExceptionLogConstant.CHAT_THREAD_CLOSED, threadId);
        return new ConflictException(
                ExceptionDomainKazanError.STA_CHAT_THREAD_CLOSED,
                Map.of(ChatConstant.FIELD_THREAD_ID, threadId));
    }

    /**
     * Logs and builds the error of a broken message limit.
     */
    private ValidationException limitExceeded(String field, int limit, int actual) {
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                ChatExceptionLogConstant.CHAT_MESSAGE_LIMIT_EXCEEDED, field, limit, actual);
        return new ValidationException(
                ExceptionDomainKazanError.VAL_CHAT_MESSAGE_INVALID,
                Map.of(field, limit));
    }

    private static boolean isOwnerOrAdmin(OrganizationMemberRole role) {
        return role == OrganizationMemberRole.OWNER || role == OrganizationMemberRole.ADMIN;
    }

    private static int countGraphemes(String text) {
        BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(text);
        int count = 0;
        while (iterator.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }
}
