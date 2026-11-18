package com.korur.oba.apps.kazan.chat.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kazan.chat.constant.service.ChatServiceLogConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatThreadCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatThreadStatusUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatThreadUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatThreadResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kazan.chat.event.ChatThreadChangedEvent;
import com.korur.oba.apps.kazan.chat.helper.ChatThreadStatusResolver;
import com.korur.oba.apps.kazan.chat.mapper.ChatThreadMapper;
import com.korur.oba.apps.kazan.chat.mapper.ChatThreadMapperHelper;
import com.korur.oba.apps.kazan.chat.repository.ChatReadStateRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatThreadService;
import com.korur.oba.apps.kazan.chat.validator.ChatValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Implementation of chat thread write operations.
 *
 * Membership rows are written through {@link ChatThreadMemberRepository} with an
 * insert that ignores existing members, as a send that mentions a user does,
 * so a join racing a mention cannot fail on the unique key. A new thread is
 * inserted the same way, so two members starting a thread on one message at
 * once get the thread-exists conflict, not a unique key error. The status is
 * written by a statement of its own, so a rename and a status change never
 * overwrite each other. Events are published inside the transaction and
 * consumed after the commit. Every write needs the MEMBER role and a channel
 * the user can read: the root message's channel for a new thread, the
 * thread's channel otherwise; a thread lives in the channel of its root.
 */
@Service
@RequiredArgsConstructor
public class ChatThreadServiceImpl implements ChatThreadService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatThreadServiceImpl.class);

    private final ChatThreadRepository chatThreadRepository;
    private final ChatThreadMemberRepository chatThreadMemberRepository;
    private final ChatReadStateRepository chatReadStateRepository;
    private final ChatValidator chatValidator;
    private final ChatThreadMapper chatThreadMapper;
    private final ChatThreadMapperHelper chatThreadMapperHelper;
    private final ChatThreadStatusResolver chatThreadStatusResolver;
    private final ChatChannelQueryService chatChannelQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public ChatThreadResponse createThread(User user, UUID organizationId, ChatThreadCreateRequest request) {
        ChatMessage root = chatValidator.ensureMessageExists(organizationId, request.rootMessageId());
        chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), root.getChannelId());
        chatValidator.ensureThreadRoot(root);
        chatValidator.ensureNoThreadOn(root.getId());

        ChatThread newThread = chatThreadMapper.toEntity(request);
        chatThreadMapperHelper.setCreateRelations(newThread, request, root, user, organizationId, Instant.now());
        UUID threadId = UUID.randomUUID();
        if (chatThreadRepository.insertIfAbsent(threadId, newThread.getTitle(), organizationId, root.getChannelId(),
                root.getId(), user.getId(), newThread.getLastMessageAt(), newThread.getAutoArchiveMinutes()) == 0) {
            // A concurrent request started a thread on this message after the
            // check above and has committed it; report it as the check does.
            chatValidator.ensureNoThreadOn(root.getId());
        }
        // Read back the stored row, which the member rows below refer to. Had
        // the conflicting thread vanished again, this fails as not found.
        ChatThread thread = chatValidator.ensureThreadExists(organizationId, threadId);

        Set<UUID> firstMembers = new LinkedHashSet<>();
        firstMembers.add(user.getId());
        // The root's author joins only while they can still read the channel:
        // a user removed from a private channel, or no longer an active
        // member, must not be told of a thread there.
        UUID rootAuthorId = root.getAuthor().getId();
        if (!rootAuthorId.equals(user.getId())
                && chatChannelQueryService.canRead(organizationId, rootAuthorId, root.getChannelId())) {
            firstMembers.add(rootAuthorId);
        }
        List<UUID> joined = new ArrayList<>();
        for (UUID memberId : firstMembers) {
            if (chatThreadMemberRepository.insertIfAbsent(thread.getId(), memberId) > 0) {
                joined.add(memberId);
            }
        }

        publishThreadEvent(ChatThreadChangedEvent.Type.CREATED, thread, joined);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_THREAD_CREATED,
                thread.getId(), root.getId());

        return chatThreadMapperHelper.toResponse(thread, user, organizationId);
    }

    @Override
    @Transactional
    public ChatThreadResponse updateThread(User user, UUID organizationId, UUID threadId,
                                           ChatThreadUpdateRequest request) {
        ChatThread thread = requireThread(user, organizationId, threadId);
        OrganizationMemberRole role = organizationMemberQueryService.findMember(organizationId, user.getId())
                .map(OrganizationMember::getRole)
                .orElse(null);
        chatValidator.ensureCanManageThread(thread, user, role);
        chatValidator.ensureAutoArchiveMinutesValid(threadId, request.autoArchiveMinutes());

        chatThreadMapper.updateEntity(thread, request);
        chatThreadMapperHelper.normalizeTitle(thread);
        thread = chatThreadRepository.save(thread);

        publishThreadEvent(ChatThreadChangedEvent.Type.UPDATED, thread, List.of());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_THREAD_UPDATED,
                threadId, thread.getAutoArchiveMinutes());

        return chatThreadMapperHelper.toResponse(thread, user, organizationId);
    }

    @Override
    @Transactional
    public ChatThreadResponse changeStatus(User user, UUID organizationId, UUID threadId,
                                           ChatThreadStatusUpdateRequest request) {
        ChatThread thread = requireThread(user, organizationId, threadId);
        OrganizationMemberRole role = organizationMemberQueryService.findMember(organizationId, user.getId())
                .map(OrganizationMember::getRole)
                .orElse(null);
        chatValidator.ensureCanManageThread(thread, user, role);

        Instant now = Instant.now();
        ChatThreadStatus current = chatThreadStatusResolver.resolve(thread, now);
        if (current == request.status()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_THREAD_STATUS_UNCHANGED,
                    current, threadId);
            return chatThreadMapperHelper.toResponse(thread, user, organizationId);
        }
        if (request.status() == ChatThreadStatus.ARCHIVED && thread.getStatus() == ChatThreadStatus.CLOSED) {
            throw closedThreadNotArchivable(threadId);
        }

        // The statement skips a CLOSED row for an archive, so a close that
        // committed after the read above still wins and the archive is refused.
        if (chatThreadRepository.updateStatus(threadId, request.status().name(), user.getId(), now) == 0) {
            throw closedThreadNotArchivable(threadId);
        }
        // The statement cleared the persistence context; read the stored row
        // again so the response carries the new status.
        thread = chatValidator.ensureThreadExists(organizationId, threadId);

        // UPDATED also refreshes the unread counts of the thread's online
        // members, as only active threads count.
        publishThreadEvent(ChatThreadChangedEvent.Type.UPDATED, thread, List.of());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_THREAD_STATUS_CHANGED,
                threadId, current, request.status(), user.getId());

        return chatThreadMapperHelper.toResponse(thread, user, organizationId);
    }

    @Override
    @Transactional
    public ChatThreadResponse joinThread(User user, UUID organizationId, UUID threadId) {
        ChatThread thread = requireThread(user, organizationId, threadId);

        if (chatThreadMemberRepository.insertIfAbsent(threadId, user.getId()) > 0) {
            publishThreadEvent(ChatThreadChangedEvent.Type.MEMBERS_JOINED, thread, List.of(user.getId()));
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_THREAD_JOINED,
                    threadId, user.getId());
        }

        return chatThreadMapperHelper.toResponse(thread, user, organizationId);
    }

    @Override
    @Transactional
    public ChatThreadResponse leaveThread(User user, UUID organizationId, UUID threadId) {
        ChatThread thread = requireThread(user, organizationId, threadId);

        if (chatThreadMemberRepository.deleteByThreadIdAndUserId(threadId, user.getId()) > 0) {
            // The read position goes with the membership: should the user
            // join again, unread counts from the new join time, as for a
            // first join.
            chatReadStateRepository.deleteByUserIdAndThreadId(user.getId(), threadId);
            publishThreadEvent(ChatThreadChangedEvent.Type.MEMBER_LEFT, thread, List.of(user.getId()));
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_THREAD_LEFT,
                    threadId, user.getId());
        }

        return chatThreadMapperHelper.toResponse(thread, user, organizationId);
    }

    /**
     * Loads a thread of the organization and checks that the user may write
     * in its channel.
     */
    private ChatThread requireThread(User user, UUID organizationId, UUID threadId) {
        ChatThread thread = chatValidator.ensureThreadExists(organizationId, threadId);
        chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), thread.getChannelId());
        return thread;
    }

    /**
     * Builds the refusal to archive a closed thread. A reply revives an
     * archived thread, so a closed thread that could be archived would reopen
     * with the next reply; it has to be reopened first.
     */
    private ConflictException closedThreadNotArchivable(UUID threadId) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                ChatExceptionLogConstant.CHAT_THREAD_CLOSED_NOT_ARCHIVABLE, threadId);
        return new ConflictException(
                ExceptionDomainKazanError.STA_CHAT_THREAD_CLOSED,
                Map.of(ChatConstant.FIELD_THREAD_ID, threadId));
    }

    private void publishThreadEvent(ChatThreadChangedEvent.Type type, ChatThread thread, List<UUID> userIds) {
        applicationEventPublisher.publishEvent(new ChatThreadChangedEvent(type, thread.getOrganizationId(),
                thread.getChannelId(), thread.getId(), thread.getRootMessageId(), List.copyOf(userIds)));
    }
}
