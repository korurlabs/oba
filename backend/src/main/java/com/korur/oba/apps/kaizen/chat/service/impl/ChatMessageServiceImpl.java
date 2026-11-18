package com.korur.oba.apps.kaizen.chat.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

// 2. Third-party
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kaizen.chat.constant.service.ChatServiceLogConstant;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatMessageUpdateRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatUserResponse;
import com.korur.oba.apps.kaizen.chat.dto.signal.ChatTypingSignal;
import com.korur.oba.apps.kaizen.chat.entity.ChatChannel;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessageMention;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessageTicketRef;
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;
import com.korur.oba.apps.kaizen.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kaizen.chat.event.ChatMessageChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatNotificationsChangedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatPushRequestedEvent;
import com.korur.oba.apps.kaizen.chat.event.ChatThreadChangedEvent;
import com.korur.oba.apps.kaizen.chat.helper.ChatSendRateLimiter;
import com.korur.oba.apps.kaizen.chat.helper.ChatThreadStatusResolver;
import com.korur.oba.apps.kaizen.chat.helper.ChatTokenParser;
import com.korur.oba.apps.kaizen.chat.helper.ChatTokenParser.ChatTokens;
import com.korur.oba.apps.kaizen.chat.mapper.ChatMessageMapper;
import com.korur.oba.apps.kaizen.chat.mapper.ChatMessageMapperHelper;
import com.korur.oba.apps.kaizen.chat.repository.ChatChannelMemberRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.chat.service.ChatMessageService;
import com.korur.oba.apps.kaizen.chat.validator.ChatValidator;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShareReviewer;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.service.TicketQueryService;
import com.korur.oba.connect.push.enums.PushKind;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.connect.realtime.service.RealtimeSignalBus;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.dto.request.attachment.AttachmentCreateRequest;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

/**
 * Implementation of chat message write operations.
 *
 * Each write runs in one transaction with everything it causes: mention rows,
 * ticket snapshots, uploaded files, and thread membership and counters. A
 * failure anywhere, a refused upload included, therefore saves nothing. Every
 * operation first checks the channel through
 * {@link ChatChannelQueryService#verifyChannelAccess}, the request's channel
 * for a send and the message's channel otherwise, so a channel the user cannot
 * read is reported as not found. That check covers the channel only: the
 * organization role is checked once before the call, by the HTTP interceptor
 * for a REST request, by the typing controller for a typing frame and by the
 * daily update service for a daily update's entry. Mentions, the here
 * audience and the quoted author who joins a thread are limited to the
 * channel's readers: every active member for a public channel, the member
 * list for a private one. A send leaves the author's read position alone;
 * the client marks read when the reader reaches the bottom. Events are
 * published inside the transaction and consumed after the commit, so a
 * rolled-back write never reaches a client.
 *
 * Thread membership is written here through {@link ChatThreadMemberRepository}, the
 * chat domain's own repository, with an insert that ignores existing members,
 * so concurrent sends that mention the same user cannot fail on the unique
 * key. A thread's reply counter and activity time are changed by single
 * UPDATE statements, never on the loaded entity, so concurrent sends and
 * deletes in one thread all count.
 * A CLOSED thread refuses new replies and edits; deleting stays allowed.
 * The pin is changed by one conditional UPDATE as well, so a pin raced by a
 * second member is stored and announced once.
 *
 * A send carries a client nonce, and a send repeated with the same nonce by
 * the same author, after a lost response, is answered with the message the
 * first one stored and changes nothing. Because two such sends can also
 * arrive together, a send runs in its own transaction from a template rather
 * than under the annotation: the send that loses on the unique index must
 * see its transaction rolled back before it reads the winner's row in a new
 * one, which no catch inside the failed transaction can do.
 */
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatMessageServiceImpl.class);

    private final ChatMessageRepository chatMessageRepository;
    private final ChatThreadRepository chatThreadRepository;
    private final ChatThreadMemberRepository chatThreadMemberRepository;
    private final ChatChannelMemberRepository chatChannelMemberRepository;
    private final ChatValidator chatValidator;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageMapperHelper chatMessageMapperHelper;
    private final ChatTokenParser chatTokenParser;
    private final ChatThreadStatusResolver chatThreadStatusResolver;
    private final ChatSendRateLimiter chatSendRateLimiter;
    private final ChatChannelQueryService chatChannelQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final TicketQueryService ticketQueryService;
    private final AttachmentService attachmentService;
    private final AttachmentQueryService attachmentQueryService;
    private final ResourceAccessQueryService resourceAccessQueryService;
    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final RealtimeSignalBus realtimeSignalBus;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final LoggingService loggingService;

    @Override
    public ChatMessageResponse postMessage(User user, UUID organizationId, ChatMessageCreateRequest request,
                                           List<MultipartFile> files) {
        try {
            return transactionTemplate.execute((TransactionStatus status) ->
                    findRepeatedSend(user, organizationId, request.clientNonce())
                            .orElseGet(() -> storeMessage(user, organizationId, request, files)));
        } catch (DataIntegrityViolationException exception) {
            // Only the nonce index means a concurrent send of the same message
            // won. Inside a caller's transaction the rollback is the caller's,
            // so the stored row cannot be read here and the failure goes up.
            if (!isClientNonceConflict(exception) || TransactionSynchronizationManager.isActualTransactionActive()) {
                throw exception;
            }
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_MESSAGE_SEND_RACE_LOST,
                    request.clientNonce(), user.getId());
            return transactionTemplate.execute((TransactionStatus status) ->
                    findRepeatedSend(user, organizationId, request.clientNonce()).orElseThrow(() -> exception));
        }
    }

    /**
     * Answers a send whose nonce the author already used, before the rate
     * limit and every write, so a repeat stores, uploads, moves and announces
     * nothing. The channel access is checked again on the stored message's
     * channel, so an author who lost access since the first send gets the
     * same not-found as for any other read of that channel.
     *
     * A repeat of a message deleted since the first send gets the deleted
     * placeholder, the state every other reader already has: the first send
     * succeeded, so an error would make the client offer a retry that can
     * never succeed, and a new copy would bring back a message its author
     * removed.
     *
     * @return the stored message as the sender sees it; empty when the nonce is new
     */
    private Optional<ChatMessageResponse> findRepeatedSend(User user, UUID organizationId, String clientNonce) {
        Optional<ChatMessage> stored = chatMessageRepository.findByAuthorIdAndClientNonceWithDeleted(user.getId(),
                clientNonce);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        ChatMessage message = stored.get();
        chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), message.getChannelId());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_MESSAGE_SEND_REPEATED,
                message.getId(), clientNonce, message.isDeleted());
        return Optional.of(chatMessageMapperHelper.toResponse(message, user, organizationId, clientNonce));
    }

    /**
     * Tells whether an integrity failure is the unique index on author and
     * client nonce, and not any other constraint of the message or its rows.
     */
    private static boolean isClientNonceConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return ChatConstant.CLIENT_NONCE_UNIQUE_INDEX.equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    private ChatMessageResponse storeMessage(User user, UUID organizationId, ChatMessageCreateRequest request,
                                             List<MultipartFile> files) {
        ChatChannel channel = chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(),
                request.channelId());
        chatSendRateLimiter.acquire(user.getId());

        List<MultipartFile> attachedFiles = files == null ? List.of() : files;
        ChatTokens tokens = chatTokenParser.parse(request.content());
        chatValidator.ensureWithinLimits(user.getId(), request.content(), attachedFiles.size(), tokens,
                request.dailyUpdateId(), request.pullRequestShareId());
        if (request.pullRequestShareId() != null) {
            chatValidator.ensurePullRequestShareAlone(request.pullRequestShareId(), request.threadId(),
                    attachedFiles.size(), request.dailyUpdateId());
        }

        ChatThread thread = request.threadId() == null
                ? null
                : chatValidator.ensureThreadExists(organizationId, request.threadId());
        if (thread != null) {
            chatValidator.ensureThreadInChannel(thread, channel.getId());
            chatValidator.ensureThreadOpen(thread);
        }
        ChatMessage replyTarget = null;
        if (request.replyToMessageId() != null) {
            replyTarget = chatValidator.ensureMessageExists(organizationId, request.replyToMessageId());
            ensureReplyInChannel(replyTarget, channel);
            chatValidator.ensureReplyInConversation(replyTarget, request.threadId());
        }
        if (request.dailyUpdateId() != null) {
            ensureDailyUpdateOutsideThread(request);
            chatValidator.ensureDailyUpdateEntry(user, organizationId, channel, request.dailyUpdateId());
        }
        PullRequestShare pullRequestShare = request.pullRequestShareId() == null
                ? null
                : chatValidator.ensurePullRequestShareEntry(user, organizationId, channel,
                        request.pullRequestShareId());
        ensureMentionsAreReaders(organizationId, channel, tokens);
        Map<UUID, Ticket> ticketsById = resolveTickets(user, organizationId, tokens);

        ChatMessage message = chatMessageMapper.toEntity(request);
        chatMessageMapperHelper.setCreateRelations(message, user, organizationId, thread, tokens, ticketsById,
                onlineReaderIds(organizationId, channel, tokens.mentionsHere()));
        if (pullRequestShare != null) {
            // The share's reviewers are active members, and the pull requests
            // channel is public, so every reviewer reads the channel.
            chatMessageMapperHelper.setPullRequestShareMentions(message, reviewerIds(pullRequestShare));
        }
        // Flushed at once: the upload's access check and the thread member
        // rows refer to the stored row.
        message = chatMessageRepository.saveAndFlush(message);

        // The thread is joined and the reply counted before any file is
        // uploaded: the counter refuses a thread closed since the check above,
        // and that refusal must come before a blob is written to storage.
        if (thread != null) {
            List<UUID> joining = new ArrayList<>();
            joining.add(user.getId());
            joining.addAll(tokens.userIds());
            quotedAuthorToJoin(organizationId, channel, user, tokens, replyTarget).ifPresent(joining::add);
            addThreadMembers(thread, joining);
            recordThreadReply(user, thread, message);
        }
        if (!attachedFiles.isEmpty()) {
            // The upload checks the sender's role, the channel and the message rules on the stored message.
            UUID messageId = message.getId();
            attachmentService.uploadAttachments(user, organizationId, toAttachmentRequests(messageId, attachedFiles),
                    attachedFiles);
        }

        publishMessageEvent(ChatMessageChangedEvent.Type.CREATED, message, request.clientNonce());
        publishPushRequest(user, organizationId, channel, thread, message, tokens, replyTarget, pullRequestShare,
                !attachedFiles.isEmpty());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_MESSAGE_CREATED,
                message.getId(), organizationId, thread == null ? null : thread.getId(),
                message.getMentions().size(), message.getTicketRefs().size(), attachedFiles.size());
        if (request.dailyUpdateId() != null) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_DAILY_UPDATE_MESSAGE_POSTED,
                    message.getId(), request.dailyUpdateId(), organizationId);
        }
        if (request.pullRequestShareId() != null) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    ChatServiceLogConstant.CHAT_PULL_REQUEST_SHARE_MESSAGE_POSTED,
                    message.getId(), request.pullRequestShareId(), organizationId);
        }

        return chatMessageMapperHelper.toResponse(message, user, organizationId, request.clientNonce());
    }

    @Override
    @Transactional
    public ChatMessageResponse editMessage(User user, UUID organizationId, UUID messageId,
                                           ChatMessageUpdateRequest request) {
        ChatMessage message = chatValidator.ensureMessageExists(organizationId, messageId);
        ChatChannel channel = chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(),
                message.getChannelId());
        chatValidator.ensureNotDailyUpdate(message);
        chatValidator.ensureNotPullRequestShare(message);
        chatValidator.ensureAuthor(message, user);
        if (message.getThread() != null) {
            chatValidator.ensureThreadOpen(message.getThread());
        }

        ChatTokens tokens = chatTokenParser.parse(request.content());
        chatValidator.ensureWithinLimits(user.getId(), request.content(),
                countStoredFiles(user, message, request.content()), tokens, null, null);
        ensureMentionsAreReaders(organizationId, channel, tokens);
        Map<UUID, Ticket> ticketsById = resolveTickets(user, organizationId, tokens);

        Set<UUID> mentionedBefore = directMentionIds(message);
        Set<UUID> notifiedBefore = mentionedUserIds(message);
        boolean everyoneBefore = message.isMentionsEveryone();
        chatMessageMapper.updateEntity(message, request);
        // Only a here mention new to this edit reaches the readers online
        // now; one that stays keeps the users it reached when it was sent.
        chatMessageMapperHelper.setUpdateRelations(message, tokens, ticketsById,
                onlineReaderIds(organizationId, channel, tokens.mentionsHere() && !message.isMentionsHere()));
        message.setEditedAt(Instant.now());
        message = chatMessageRepository.save(message);

        if (message.getThread() != null) {
            List<UUID> newlyMentioned = tokens.userIds().stream()
                    .filter(userId -> !mentionedBefore.contains(userId))
                    .toList();
            addThreadMembers(message.getThread(), newlyMentioned);
        }

        publishMessageEvent(ChatMessageChangedEvent.Type.UPDATED, message, null);
        publishNotificationChanges(message, notifiedBefore, everyoneBefore);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_MESSAGE_EDITED,
                messageId, message.getMentions().size(), message.getTicketRefs().size());

        return chatMessageMapperHelper.toResponse(message, user, organizationId, null);
    }

    @Override
    @Transactional
    public void deleteMessage(User user, UUID organizationId, UUID messageId) {
        ChatMessage message = chatValidator.ensureMessageExists(organizationId, messageId);
        chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), message.getChannelId());
        chatValidator.ensureNotDailyUpdate(message);
        chatValidator.ensureNotPullRequestShare(message);
        chatValidator.ensureCanDelete(message, user, findRole(organizationId, user));

        softDelete(message);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_MESSAGE_DELETED, messageId);
    }

    @Override
    @Transactional
    public ChatMessageResponse setPinned(User user, UUID organizationId, UUID messageId, boolean pinned) {
        ChatMessage message = chatValidator.ensureMessageExists(organizationId, messageId);
        chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), message.getChannelId());
        chatValidator.ensureCanPin(message, user, findRole(organizationId, user));

        if (pinned == (message.getPinnedAt() != null)) {
            return chatMessageMapperHelper.toResponse(message, user, organizationId, null);
        }
        if (pinned) {
            chatValidator.ensurePinLimitNotReached(message.getChannelId());
        }
        // One conditional statement: of two members pinning at the same
        // moment only the one that changed the row announces it.
        int changed = pinned
                ? chatMessageRepository.pinIfUnpinned(messageId, Instant.now(), user.getId())
                : chatMessageRepository.unpinIfPinned(messageId);
        // The statement cleared the persistence context; read the stored row
        // again so the response carries the current pin.
        message = chatValidator.ensureMessageExists(organizationId, messageId);
        if (changed == 0) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_MESSAGE_PIN_UNCHANGED,
                    messageId, pinned, user.getId());
            return chatMessageMapperHelper.toResponse(message, user, organizationId, null);
        }

        publishMessageEvent(pinned ? ChatMessageChangedEvent.Type.PINNED : ChatMessageChangedEvent.Type.UNPINNED,
                message, null);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                pinned ? ChatServiceLogConstant.CHAT_MESSAGE_PINNED : ChatServiceLogConstant.CHAT_MESSAGE_UNPINNED,
                messageId, user.getId());

        return chatMessageMapperHelper.toResponse(message, user, organizationId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public void signalTyping(UUID userId, UUID organizationId, UUID channelId, UUID threadId) {
        ChatChannel channel = chatChannelQueryService.verifyChannelAccess(organizationId, userId, channelId);
        if (threadId != null) {
            chatValidator.ensureThreadInChannel(chatValidator.ensureThreadExists(organizationId, threadId),
                    channel.getId());
        }

        // The channel's own topic, so typing in a private channel reaches
        // only its members.
        realtimeSignalBus.publishToTopic(
                RealtimeDestinationUtil.organizationTopic(organizationId,
                        ChatConstant.CHAT_CHANNEL_TOPIC_SUFFIX_PREFIX + channel.getId()),
                ChatTypingSignal.of(userId, threadId));
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_TYPING_PUBLISHED,
                userId, organizationId, channelId, threadId);
    }

    @Override
    @Transactional
    public void touchDailyUpdateMessage(DailyUpdate dailyUpdate) {
        Optional<ChatMessage> found = chatMessageRepository.findByDailyUpdateId(dailyUpdate.getId());
        if (found.isEmpty()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE,
                    ChatServiceLogConstant.CHAT_DAILY_UPDATE_MESSAGE_ABSENT, dailyUpdate.getId());
            return;
        }
        ChatMessage message = found.get();
        message.setEditedAt(dailyUpdate.getEditedAt() == null ? Instant.now() : dailyUpdate.getEditedAt());
        message = chatMessageRepository.save(message);

        publishMessageEvent(ChatMessageChangedEvent.Type.UPDATED, message, null);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_DAILY_UPDATE_MESSAGE_TOUCHED,
                message.getId(), dailyUpdate.getId());
    }

    @Override
    @Transactional
    public void removeDailyUpdateMessage(DailyUpdate dailyUpdate) {
        Optional<ChatMessage> found = chatMessageRepository.findByDailyUpdateId(dailyUpdate.getId());
        if (found.isEmpty()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE,
                    ChatServiceLogConstant.CHAT_DAILY_UPDATE_MESSAGE_ABSENT, dailyUpdate.getId());
            return;
        }
        ChatMessage message = found.get();
        softDelete(message);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_DAILY_UPDATE_MESSAGE_REMOVED,
                message.getId(), dailyUpdate.getId());
    }

    @Override
    @Transactional
    public void touchPullRequestShareMessage(PullRequestShare share, Set<UUID> reviewersBefore, boolean edited) {
        Optional<ChatMessage> found = chatMessageRepository.findByPullRequestShareId(share.getId());
        if (found.isEmpty()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE,
                    ChatServiceLogConstant.CHAT_PULL_REQUEST_SHARE_MESSAGE_ABSENT, share.getId());
            return;
        }
        ChatMessage message = found.get();
        if (edited) {
            message.setEditedAt(share.getEditedAt() == null ? Instant.now() : share.getEditedAt());
            chatMessageMapperHelper.setPullRequestShareMentions(message, reviewerIds(share));
            message = chatMessageRepository.save(message);
        } else {
            // Nothing on the message changes, so JPA auditing would not move
            // its version; one statement moves it for the readers' refetch.
            chatMessageRepository.touchUpdatedAt(message.getId());
        }

        publishMessageEvent(ChatMessageChangedEvent.Type.UPDATED, message, null);
        if (edited) {
            publishNotificationChanges(message, reviewersBefore, false);
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                ChatServiceLogConstant.CHAT_PULL_REQUEST_SHARE_MESSAGE_TOUCHED, message.getId(), share.getId(),
                edited);
    }

    @Override
    @Transactional
    public void removePullRequestShareMessage(PullRequestShare share) {
        Optional<ChatMessage> found = chatMessageRepository.findByPullRequestShareId(share.getId());
        if (found.isEmpty()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE,
                    ChatServiceLogConstant.CHAT_PULL_REQUEST_SHARE_MESSAGE_ABSENT, share.getId());
            return;
        }
        ChatMessage message = found.get();
        softDelete(message);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                ChatServiceLogConstant.CHAT_PULL_REQUEST_SHARE_MESSAGE_REMOVED, message.getId(), share.getId());
    }

    /**
     * The user's role in the organization, for the rules that let owners and
     * admins act on other members' messages; null when the user is no member.
     */
    private OrganizationMemberRole findRole(UUID organizationId, User user) {
        return organizationMemberQueryService.findMember(organizationId, user.getId())
                .map(OrganizationMember::getRole)
                .orElse(null);
    }

    /**
     * Ensures every user mentioned directly can read the channel; the readers
     * are read only when someone is mentioned.
     */
    private void ensureMentionsAreReaders(UUID organizationId, ChatChannel channel, ChatTokens tokens) {
        if (tokens.userIds().isEmpty()) {
            return;
        }
        chatValidator.ensureMentionsAreMembers(organizationId, tokens.userIds(),
                channelReaderIds(organizationId, channel));
    }

    /**
     * The users who can read a channel: every active member of the
     * organization for a public channel, the active members on the member
     * list for a private one.
     */
    private Set<UUID> channelReaderIds(UUID organizationId, ChatChannel channel) {
        Set<UUID> readerIds = new LinkedHashSet<>();
        for (OrganizationMemberSummaryProjection member
                : organizationMemberQueryService.listActiveMemberSummaries(organizationId, Instant.now())) {
            readerIds.add(member.getUserId());
        }
        if (channel.getVisibility() == ChatChannelVisibility.PRIVATE) {
            readerIds.retainAll(new HashSet<>(chatChannelMemberRepository.findUserIdsByChannelId(channel.getId())));
        }
        return readerIds;
    }

    /**
     * Ensures the quoted message lies in the channel posted to. A message of
     * another channel is reported as not found, so a reply never shows the
     * text of a channel the sender may not read.
     */
    private void ensureReplyInChannel(ChatMessage replyTarget, ChatChannel channel) {
        if (replyTarget.getChannelId().equals(channel.getId())) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ChatExceptionLogConstant.CHAT_MESSAGE_NOT_FOUND,
                replyTarget.getId(), replyTarget.getOrganizationId());
        throw new NotFoundException(
                ExceptionDomainKaizenError.NOT_CHAT_MESSAGE_FOUND,
                Map.of(ChatConstant.FIELD_MESSAGE_ID, replyTarget.getId()));
    }

    /**
     * Refuses a daily update entry inside a thread: the entry is a card of
     * the daily updates channel itself, and the schema allows no entry with
     * a thread.
     */
    private void ensureDailyUpdateOutsideThread(ChatMessageCreateRequest request) {
        if (request.threadId() == null) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ChatExceptionLogConstant.CHAT_DAILY_UPDATE_IN_THREAD,
                request.dailyUpdateId(), request.threadId());
        throw new ValidationException(
                ExceptionDomainKaizenError.VAL_CHAT_DAILY_UPDATE_CHANNEL,
                Map.of(ChatConstant.FIELD_THREAD_ID, request.threadId()));
    }

    /**
     * Resolves the referenced tickets among those on the author's boards of
     * the chat's organization; a ticket the author cannot reach there is
     * reported as not found.
     */
    private Map<UUID, Ticket> resolveTickets(User user, UUID organizationId, ChatTokens tokens) {
        if (tokens.ticketIds().isEmpty()) {
            return Map.of();
        }
        List<Ticket> tickets = ticketQueryService.getAccessibleTickets(user.getId(), organizationId, tokens.ticketIds());
        chatValidator.ensureTicketsAccessible(tokens.ticketIds(), tickets);
        Map<UUID, Ticket> ticketsById = new HashMap<>();
        for (Ticket ticket : tickets) {
            ticketsById.put(ticket.getId(), ticket);
        }
        return ticketsById;
    }

    /**
     * The channel's readers online in the organization, whom a here mention
     * reaches: every online member for a public channel, the online members
     * on the list for a private one; empty when the message adds no here
     * mention.
     */
    private Collection<UUID> onlineReaderIds(UUID organizationId, ChatChannel channel, boolean needed) {
        if (!needed) {
            return Set.of();
        }
        Set<UUID> online = new LinkedHashSet<>(realtimeSessionRegistry.onlineUserIds(organizationId));
        if (channel.getVisibility() == ChatChannelVisibility.PRIVATE) {
            online.retainAll(new HashSet<>(chatChannelMemberRepository.findUserIdsByChannelId(channel.getId())));
        }
        return online;
    }

    /**
     * Counts the files a message keeps through an edit. Read only when the new
     * text is empty, because an empty text is allowed only beside files. The
     * caller has checked the message and its channel for this editor, so the
     * read runs under a read grant for it.
     */
    private int countStoredFiles(User user, ChatMessage message, String content) {
        if (content != null && !content.isBlank()) {
            return 0;
        }
        return resourceAccessQueryService.withReadGrant(user, ResourceAccessType.CHAT_MESSAGE,
                List.of(message.getId()),
                () -> attachmentQueryService.listAttachmentsByEntity(user, message.getOrganizationId(),
                        StorageEntityType.CHAT_MESSAGE, message.getId())).size();
    }

    private static List<AttachmentCreateRequest> toAttachmentRequests(UUID messageId, List<MultipartFile> files) {
        return files.stream()
                .map(file -> new AttachmentCreateRequest(file.getOriginalFilename(), StorageEntityType.CHAT_MESSAGE,
                        messageId))
                .toList();
    }

    /**
     * The author of the quoted message, who joins the thread with the reply
     * so that the reply reaches them as a mention would, also after they left
     * the thread. Empty without a quote, for a quote of the sender's own
     * message, and for an author who can no longer read the channel.
     */
    private Optional<UUID> quotedAuthorToJoin(UUID organizationId, ChatChannel channel, User sender,
                                              ChatTokens tokens, ChatMessage replyTarget) {
        if (replyTarget == null) {
            return Optional.empty();
        }
        UUID authorId = replyTarget.getAuthor().getId();
        if (authorId.equals(sender.getId())) {
            return Optional.empty();
        }
        if (tokens.userIds().contains(authorId)) {
            // Mentioned directly as well, so already checked and joining.
            return Optional.empty();
        }
        return channelReaderIds(organizationId, channel).contains(authorId) ? Optional.of(authorId) : Optional.empty();
    }

    /**
     * Publishes the users whose notification counts an edit changed: those
     * who gained or lost a mention row, and everyone the conversation reaches
     * when the everyone mention was added or removed. Nothing is published
     * when the edit notifies the same users as before.
     */
    private void publishNotificationChanges(ChatMessage message, Set<UUID> notifiedBefore, boolean everyoneBefore) {
        Set<UUID> notifiedAfter = mentionedUserIds(message);
        Set<UUID> changed = new LinkedHashSet<>();
        for (UUID userId : notifiedBefore) {
            if (!notifiedAfter.contains(userId)) {
                changed.add(userId);
            }
        }
        for (UUID userId : notifiedAfter) {
            if (!notifiedBefore.contains(userId)) {
                changed.add(userId);
            }
        }
        UUID authorId = message.getAuthor().getId();
        changed.remove(authorId);
        boolean broadcast = everyoneBefore != message.isMentionsEveryone();
        if (changed.isEmpty() && !broadcast) {
            return;
        }
        applicationEventPublisher.publishEvent(new ChatNotificationsChangedEvent(
                message.getOrganizationId(),
                message.getChannelId(),
                message.getThread() == null ? null : message.getThread().getId(),
                message.getId(),
                authorId,
                List.copyOf(changed),
                broadcast));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_NOTIFICATIONS_CHANGED,
                message.getId(), changed, broadcast);
    }

    /**
     * Publishes the pushes a new message asks for, so readers away from the
     * chat hear of a message meant for them. Resolved here, inside the send,
     * because the here audience is the readers online at send time and the
     * mention rows hold it only until the next edit.
     *
     * Each recipient gets one kind, the first that applies:
     * - MENTION: mentioned directly in the text.
     * - REPLY: author of the quoted message, or of the thread's root while
     *   still a member of the thread.
     * - REVIEW: reviewer of the pull request share the message stands for.
     * - EVERYONE: a reader of the channel when the text mentions everyone.
     * - HERE: a reader reached by the here mention, online at send time.
     * In a thread, everyone and here reach only the thread's members, as in
     * the unread counts. The author never gets a push, and only readers of
     * the channel do, so a user removed from a private channel since the
     * quoted message hears nothing of it. A message that calls on nobody
     * publishes nothing.
     *
     * A message with files and no text is marked as attachments only, so its
     * push names the attachment instead of an empty preview. A pull request
     * share and a daily update message have no text either, but stand for
     * their entry, so they are never marked.
     */
    private void publishPushRequest(User user, UUID organizationId, ChatChannel channel, ChatThread thread,
                                    ChatMessage message, ChatTokens tokens, ChatMessage replyTarget,
                                    PullRequestShare pullRequestShare, boolean hasFiles) {
        Set<UUID> threadMemberIds = thread == null
                ? null
                : new HashSet<>(chatThreadMemberRepository.findUserIdsByThreadId(thread.getId()));
        Map<UUID, PushKind> recipients = new LinkedHashMap<>();
        for (UUID userId : tokens.userIds()) {
            recipients.putIfAbsent(userId, PushKind.MENTION);
        }
        if (replyTarget != null) {
            recipients.putIfAbsent(replyTarget.getAuthor().getId(), PushKind.REPLY);
        }
        if (thread != null) {
            chatMessageRepository.findByIdAndOrganizationId(thread.getRootMessageId(), organizationId)
                    .map(root -> root.getAuthor().getId())
                    .filter(threadMemberIds::contains)
                    .ifPresent(rootAuthorId -> recipients.putIfAbsent(rootAuthorId, PushKind.REPLY));
        }
        if (pullRequestShare != null) {
            for (UUID reviewerId : reviewerIds(pullRequestShare)) {
                recipients.putIfAbsent(reviewerId, PushKind.REVIEW);
            }
        }
        Set<UUID> readerIds = null;
        if (tokens.mentionsEveryone()) {
            readerIds = channelReaderIds(organizationId, channel);
            for (UUID readerId : readerIds) {
                if (threadMemberIds == null || threadMemberIds.contains(readerId)) {
                    recipients.putIfAbsent(readerId, PushKind.EVERYONE);
                }
            }
        }
        for (ChatMessageMention mention : message.getMentions()) {
            if (mention.isViaHere()
                    && (threadMemberIds == null || threadMemberIds.contains(mention.getUserId()))) {
                recipients.putIfAbsent(mention.getUserId(), PushKind.HERE);
            }
        }
        recipients.remove(user.getId());
        if (recipients.isEmpty()) {
            return;
        }
        recipients.keySet().retainAll(readerIds != null ? readerIds : channelReaderIds(organizationId, channel));
        if (recipients.isEmpty()) {
            return;
        }

        Set<UUID> namedUserIds = new LinkedHashSet<>(tokens.userIds());
        namedUserIds.add(user.getId());
        Map<UUID, ChatUserResponse> users = chatMessageMapperHelper.resolveUsers(organizationId, namedUserIds);
        String text = chatTokenParser.renderPlain(message.getContent(), users, ticketRefsByTicketId(message)).strip();
        boolean attachmentsOnly = hasFiles && text.isEmpty() && pullRequestShare == null
                && message.getDailyUpdateId() == null;
        applicationEventPublisher.publishEvent(new ChatPushRequestedEvent(
                organizationId,
                channel.getId(),
                thread == null ? null : thread.getId(),
                message.getId(),
                user.getId(),
                Map.copyOf(recipients),
                pushConversation(channel, thread),
                pushSenderName(users.get(user.getId())),
                text,
                attachmentsOnly));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_PUSH_REQUESTED,
                message.getId(), recipients.size());
    }

    /**
     * The title of a chat push: "#general" in the channel, the thread's
     * title and its channel in a thread.
     */
    private static String pushConversation(ChatChannel channel, ChatThread thread) {
        String channelName = ChatConstant.PUSH_CHANNEL_PREFIX + channel.getName();
        return thread == null ? channelName : thread.getTitle() + ChatConstant.PUSH_THREAD_SEPARATOR + channelName;
    }

    /**
     * The author's name in a push body: first and last name, the email for an
     * account without a name, and a neutral placeholder without either, so a
     * missing name never reaches a phone as "null".
     */
    private static String pushSenderName(ChatUserResponse author) {
        if (author == null) {
            return ChatConstant.PUSH_UNKNOWN_SENDER;
        }
        List<String> nameParts = new ArrayList<>();
        if (author.firstName() != null && !author.firstName().isBlank()) {
            nameParts.add(author.firstName().strip());
        }
        if (author.lastName() != null && !author.lastName().isBlank()) {
            nameParts.add(author.lastName().strip());
        }
        if (!nameParts.isEmpty()) {
            return String.join(ChatConstant.PUSH_SENDER_NAME_SEPARATOR, nameParts);
        }
        return author.email() != null && !author.email().isBlank()
                ? author.email()
                : ChatConstant.PUSH_UNKNOWN_SENDER;
    }

    /**
     * The message's ticket snapshots by ticket ID, which render its ticket
     * tokens as key and title.
     */
    private static Map<UUID, ChatMessageTicketRef> ticketRefsByTicketId(ChatMessage message) {
        Map<UUID, ChatMessageTicketRef> ticketRefs = new HashMap<>();
        for (ChatMessageTicketRef ticketRef : message.getTicketRefs()) {
            if (ticketRef.getTicketId() != null) {
                ticketRefs.put(ticketRef.getTicketId(), ticketRef);
            }
        }
        return ticketRefs;
    }

    /**
     * The reviewers of a share in display order, the mentions of its entry.
     */
    private static List<UUID> reviewerIds(PullRequestShare share) {
        List<UUID> reviewerIds = new ArrayList<>();
        for (PullRequestShareReviewer reviewer : share.getReviewers()) {
            reviewerIds.add(reviewer.getUser().getId());
        }
        return reviewerIds;
    }

    /**
     * Every user with a mention row on the message, reached directly or
     * through here.
     */
    private static Set<UUID> mentionedUserIds(ChatMessage message) {
        Set<UUID> userIds = new LinkedHashSet<>();
        for (ChatMessageMention mention : message.getMentions()) {
            userIds.add(mention.getUserId());
        }
        return userIds;
    }

    private static Set<UUID> directMentionIds(ChatMessage message) {
        Set<UUID> userIds = new LinkedHashSet<>();
        for (ChatMessageMention mention : message.getMentions()) {
            if (!mention.isViaHere()) {
                userIds.add(mention.getUserId());
            }
        }
        return userIds;
    }

    /**
     * Adds users to a thread, skipping those who already follow it, and
     * publishes the users who joined.
     */
    private void addThreadMembers(ChatThread thread, Collection<UUID> userIds) {
        List<UUID> joined = new ArrayList<>();
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            if (chatThreadMemberRepository.insertIfAbsent(thread.getId(), userId) > 0) {
                joined.add(userId);
            }
        }
        if (joined.isEmpty()) {
            return;
        }
        applicationEventPublisher.publishEvent(new ChatThreadChangedEvent(ChatThreadChangedEvent.Type.MEMBERS_JOINED,
                thread.getOrganizationId(), thread.getChannelId(), thread.getId(), thread.getRootMessageId(),
                List.copyOf(joined)));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_THREAD_MEMBERS_ADDED,
                thread.getId(), joined);
    }

    /**
     * Counts a reply on its thread. The statement refuses a thread closed
     * since the check at the start of the send, which rolls the send back. A
     * reply to an archived thread, archived by hand or by inactivity, makes it
     * active again, which moves it between the thread list tabs and back into
     * the unread counts, so the change is announced as a thread update.
     */
    private void recordThreadReply(User user, ChatThread thread, ChatMessage message) {
        boolean revives = chatThreadStatusResolver.resolve(thread, message.getCreatedAt())
                == ChatThreadStatus.ARCHIVED;
        chatValidator.ensureReplyRecorded(thread.getId(),
                chatThreadRepository.incrementMessageCount(thread.getId(), message.getCreatedAt(), user.getId()));
        if (!revives) {
            return;
        }
        applicationEventPublisher.publishEvent(new ChatThreadChangedEvent(ChatThreadChangedEvent.Type.UPDATED,
                thread.getOrganizationId(), thread.getChannelId(), thread.getId(), thread.getRootMessageId(),
                List.of()));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_THREAD_REVIVED,
                thread.getId(), user.getId());
    }

    /**
     * Soft-deletes a message for everyone: the pin is cleared, a thread reply
     * is uncounted, the row is marked deleted, and the deletion, and the
     * unpin where there was a pin, are published.
     */
    private void softDelete(ChatMessage message) {
        boolean wasPinned = message.getPinnedAt() != null;
        if (wasPinned) {
            message.setPinnedAt(null);
            message.setPinnedBy(null);
        }
        ChatThread thread = message.getThread();
        if (thread != null) {
            chatThreadRepository.decrementMessageCount(thread.getId());
        }
        // The cleared pin is written before the native soft delete, whose
        // update the managed entity would otherwise overwrite at commit.
        chatMessageRepository.saveAndFlush(message);
        // Marks only the message as deleted. A JPA delete would cascade to the
        // mentions and ticket references, and a thread started on the message
        // must keep its root row.
        chatMessageRepository.deleteByIdWithoutRelations(message.getId());

        publishMessageEvent(ChatMessageChangedEvent.Type.DELETED, message, null);
        if (wasPinned) {
            publishMessageEvent(ChatMessageChangedEvent.Type.UNPINNED, message, null);
        }
    }

    private void publishMessageEvent(ChatMessageChangedEvent.Type type, ChatMessage message, String clientNonce) {
        applicationEventPublisher.publishEvent(new ChatMessageChangedEvent(
                type,
                message.getOrganizationId(),
                message.getChannelId(),
                message.getId(),
                message.getThread() == null ? null : message.getThread().getId(),
                message.getAuthor().getId(),
                message.getCreatedAt(),
                clientNonce));
    }
}
