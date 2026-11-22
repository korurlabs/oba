package com.korur.oba.apps.kazan.chat.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageQueryRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatSearchRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessagePageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.mapper.ChatMessageMapperHelper;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatMessageQueryService;
import com.korur.oba.apps.kazan.chat.validator.ChatValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.pullrequest.enums.PullRequestShareStatus;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;
import com.korur.oba.dojo.time.BusinessCalendar.DayRange;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of chat message read operations.
 *
 * Every page query asks for one row more than the page holds; the extra row
 * only tells whether more rows follow in that direction and is dropped. The
 * repository returns the rows of a message page oldest first, so the extra
 * row is the first one on the older side and the last one on the newer side.
 *
 * Every read checks the channel first: the request's channel for a channel
 * page and the pins, the thread's or message's channel otherwise. A cursor
 * must lie in the conversation it pages, so a message ID of another channel
 * never positions a page. Search covers the channels the reader can read, or
 * the one channel the request names.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageQueryServiceImpl implements ChatMessageQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatMessageQueryServiceImpl.class);

    /**
     * The keyset order of the page queries: created_at, then ID. PostgreSQL
     * orders UUIDs by their bytes, which is the unsigned order of the two
     * halves, not the signed order of {@link UUID#compareTo}.
     */
    private static final Comparator<ChatMessage> KEYSET_ORDER = Comparator
            .comparing(ChatMessage::getCreatedAt)
            .thenComparing(ChatMessage::getId, ChatMessageQueryServiceImpl::compareUuidBytes);

    private final ChatMessageRepository chatMessageRepository;
    private final ChatChannelRepository chatChannelRepository;
    private final ChatChannelQueryService chatChannelQueryService;
    private final ChatValidator chatValidator;
    private final ChatMessageMapperHelper chatMessageMapperHelper;
    private final LoggingService loggingService;
    private final BusinessCalendar businessCalendar;

    @Override
    public ChatMessagePageResponse listChannelMessages(User user, UUID organizationId,
                                                       ChatMessageQueryRequest request) {
        ChatChannel channel = chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(),
                request.getChannelId());
        chatValidator.ensureSingleCursor(request);
        UUID channelId = channel.getId();
        // The day filter uses the business day, the day of the daily updates, so a
        // day in the channel matches the day of its updates.
        DayRange day = businessCalendar.dayRange(request.getDay(), request.getDay());
        UUID authorId = request.getAuthorId();
        List<PullRequestShareStatus> chosenStatuses = request.getPullRequestStatuses();
        boolean anyPullRequestStatus = chosenStatuses == null || chosenStatuses.isEmpty();
        List<String> pullRequestStatuses = pullRequestStatusNames(anyPullRequestStatus
                ? List.of(PullRequestShareStatus.values())
                : chosenStatuses);
        return readPage(user, organizationId, channelId, request, new PageSource() {
            @Override
            public List<ChatMessage> latest(int limit) {
                return chatMessageRepository.findLatestChannelMessages(channelId, day.start(), day.end(), authorId,
                        anyPullRequestStatus, pullRequestStatuses, limit);
            }

            @Override
            public List<ChatMessage> before(ChatMessage cursor, int limit) {
                return chatMessageRepository.findChannelMessagesBefore(channelId, day.start(), day.end(), authorId,
                        anyPullRequestStatus, pullRequestStatuses, cursor.getCreatedAt(), cursor.getId(), limit);
            }

            @Override
            public List<ChatMessage> after(ChatMessage cursor, int limit) {
                return chatMessageRepository.findChannelMessagesAfter(channelId, day.start(), day.end(), authorId,
                        anyPullRequestStatus, pullRequestStatuses, cursor.getCreatedAt(), cursor.getId(), limit);
            }

            @Override
            public List<ChatMessage> around(ChatMessage cursor, int olderLimit, int newerLimit) {
                return chatMessageRepository.findChannelMessagesAround(channelId, day.start(), day.end(), authorId,
                        anyPullRequestStatus, pullRequestStatuses, cursor.getCreatedAt(), cursor.getId(), olderLimit,
                        newerLimit);
            }
        });
    }

    @Override
    public ChatMessagePageResponse listThreadMessages(User user, UUID organizationId, UUID threadId,
                                                      ChatMessageQueryRequest request) {
        ChatThread thread = chatValidator.ensureThreadExists(organizationId, threadId);
        chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), thread.getChannelId());
        chatValidator.ensureSingleCursor(request);
        UUID id = thread.getId();
        return readPage(user, organizationId, thread.getChannelId(), request, new PageSource() {
            @Override
            public List<ChatMessage> latest(int limit) {
                return chatMessageRepository.findLatestThreadMessages(id, limit);
            }

            @Override
            public List<ChatMessage> before(ChatMessage cursor, int limit) {
                return chatMessageRepository.findThreadMessagesBefore(id, cursor.getCreatedAt(), cursor.getId(),
                        limit);
            }

            @Override
            public List<ChatMessage> after(ChatMessage cursor, int limit) {
                return chatMessageRepository.findThreadMessagesAfter(id, cursor.getCreatedAt(), cursor.getId(),
                        limit);
            }

            @Override
            public List<ChatMessage> around(ChatMessage cursor, int olderLimit, int newerLimit) {
                return chatMessageRepository.findThreadMessagesAround(id, cursor.getCreatedAt(), cursor.getId(),
                        olderLimit, newerLimit);
            }
        });
    }

    @Override
    public ChatMessageResponse getMessage(User user, UUID organizationId, UUID messageId) {
        ChatMessage message = chatValidator.ensureMessageExists(organizationId, messageId);
        chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), message.getChannelId());
        return chatMessageMapperHelper.toResponse(message, user, organizationId, null);
    }

    @Override
    public Optional<ChatMessageResponse> findNeutralMessage(UUID organizationId, UUID messageId,
                                                            String clientNonce) {
        return chatMessageRepository.findByIdAndOrganizationId(messageId, organizationId)
                .flatMap(message -> chatMessageMapperHelper.toNeutralResponse(message, clientNonce));
    }

    @Override
    public Optional<Instant> findMessageVersion(UUID organizationId, UUID messageId) {
        return chatMessageRepository.findByIdAndOrganizationId(messageId, organizationId)
                .filter(message -> !message.isDeleted())
                .map(ChatMessage::getUpdatedAt);
    }

    @Override
    public ChatMessagePageResponse listPinnedMessages(User user, UUID organizationId, UUID channelId, UUID before,
                                                      Integer limit) {
        ChatChannel channel = chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), channelId);
        int pageSize = chatValidator.ensurePageSize(limit, ChatConstant.DEFAULT_PAGE_SIZE);

        List<ChatMessage> rows;
        if (before == null) {
            rows = chatMessageRepository.findPinnedMessages(channel.getId(), pageSize + 1);
        } else {
            ChatMessage cursor = findCursor(organizationId, channel.getId(), before);
            if (cursor.getPinnedAt() == null) {
                // An unpinned message has no place in the pin order, so the
                // client reloads the list from the top.
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ChatExceptionLogConstant.CHAT_PIN_CURSOR_NOT_PINNED,
                        before, organizationId);
                throw notFound(before);
            }
            rows = chatMessageRepository.findPinnedMessagesBefore(channel.getId(), cursor.getPinnedAt(),
                    cursor.getId(), pageSize + 1);
        }
        boolean hasOlder = rows.size() > pageSize;
        List<ChatMessage> page = hasOlder ? rows.subList(0, pageSize) : rows;
        return chatMessageMapperHelper.toPageResponse(page, hasOlder, before != null, user, organizationId);
    }

    @Override
    public ChatMessagePageResponse searchMessages(User user, UUID organizationId, ChatSearchRequest request) {
        List<UUID> channelIds = readableChannelIds(user, organizationId, request.getChannelId());
        int pageSize = chatValidator.ensurePageSize(request.getLimit(), ChatConstant.DEFAULT_SEARCH_PAGE_SIZE);
        ChatMessage cursor = request.getBefore() == null
                ? null
                : findCursor(organizationId, null, request.getBefore());
        if (channelIds.isEmpty()) {
            return chatMessageMapperHelper.toPageResponse(List.of(), false, cursor != null, user, organizationId);
        }

        List<ChatMessage> rows = chatMessageRepository.searchMessages(organizationId, channelIds, request.getQ(),
                request.getAuthorId(),
                cursor == null ? null : cursor.getCreatedAt(),
                cursor == null ? null : cursor.getId(),
                pageSize + 1);
        boolean hasOlder = rows.size() > pageSize;
        List<ChatMessage> page = hasOlder ? rows.subList(0, pageSize) : rows;
        return chatMessageMapperHelper.toPageResponse(page, hasOlder, cursor != null, user, organizationId);
    }

    /**
     * Reads one keyset page of a conversation. A page next to a cursor always
     * has more rows on the cursor's side, so only the far side is probed.
     */
    private ChatMessagePageResponse readPage(User user, UUID organizationId, UUID channelId,
                                             ChatMessageQueryRequest request, PageSource source) {
        int pageSize = chatValidator.ensurePageSize(request.getLimit(), ChatConstant.DEFAULT_PAGE_SIZE);

        if (request.getBefore() != null) {
            ChatMessage cursor = findCursor(organizationId, channelId, request.getBefore());
            List<ChatMessage> rows = source.before(cursor, pageSize + 1);
            boolean hasOlder = rows.size() > pageSize;
            return toPage(user, organizationId, hasOlder ? dropFirst(rows) : rows, hasOlder, true);
        }
        if (request.getAfter() != null) {
            ChatMessage cursor = findCursor(organizationId, channelId, request.getAfter());
            List<ChatMessage> rows = source.after(cursor, pageSize + 1);
            boolean hasNewer = rows.size() > pageSize;
            return toPage(user, organizationId, hasNewer ? dropLast(rows) : rows, true, hasNewer);
        }
        if (request.getAround() != null) {
            ChatMessage cursor = findCursor(organizationId, channelId, request.getAround());
            int olderLimit = pageSize / 2;
            int newerLimit = pageSize - olderLimit;
            List<ChatMessage> rows = source.around(cursor, olderLimit + 1, newerLimit + 1);

            List<ChatMessage> older = new ArrayList<>();
            List<ChatMessage> newer = new ArrayList<>();
            for (ChatMessage row : rows) {
                if (KEYSET_ORDER.compare(row, cursor) < 0) {
                    older.add(row);
                } else {
                    newer.add(row);
                }
            }
            boolean hasOlder = older.size() > olderLimit;
            boolean hasNewer = newer.size() > newerLimit;
            List<ChatMessage> page = new ArrayList<>(hasOlder ? dropFirst(older) : older);
            page.addAll(hasNewer ? dropLast(newer) : newer);
            return toPage(user, organizationId, page, hasOlder, hasNewer);
        }

        List<ChatMessage> rows = source.latest(pageSize + 1);
        boolean hasOlder = rows.size() > pageSize;
        return toPage(user, organizationId, hasOlder ? dropFirst(rows) : rows, hasOlder, false);
    }

    private ChatMessagePageResponse toPage(User user, UUID organizationId, List<ChatMessage> messages,
                                           boolean hasOlder, boolean hasNewer) {
        return chatMessageMapperHelper.toPageResponse(messages, hasOlder, hasNewer, user, organizationId);
    }

    /**
     * The channels a search covers: the one channel the request names, after
     * the access check, or every channel the reader can read.
     */
    private List<UUID> readableChannelIds(User user, UUID organizationId, UUID channelId) {
        if (channelId != null) {
            return List.of(chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), channelId)
                    .getId());
        }
        return chatChannelRepository.findReadableChannelIds(organizationId, user.getId());
    }

    /**
     * Reads a cursor message, deleted ones included, so the page next to a
     * message deleted meanwhile still loads. A cursor of another organization,
     * or of another channel when a channel is given, is reported as not found.
     */
    private ChatMessage findCursor(UUID organizationId, UUID channelId, UUID messageId) {
        return chatMessageRepository.findByIdWithDeleted(messageId)
                .filter(message -> organizationId.equals(message.getOrganizationId()))
                .filter(message -> channelId == null || channelId.equals(message.getChannelId()))
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ChatExceptionLogConstant.CHAT_MESSAGE_NOT_FOUND,
                            messageId, organizationId);
                    return notFound(messageId);
                });
    }

    private static NotFoundException notFound(UUID messageId) {
        return new NotFoundException(ExceptionDomainKazanError.NOT_CHAT_MESSAGE_FOUND,
                Map.of(ChatConstant.FIELD_MESSAGE_ID, messageId));
    }

    private static List<ChatMessage> dropFirst(List<ChatMessage> rows) {
        return rows.subList(1, rows.size());
    }

    private static List<ChatMessage> dropLast(List<ChatMessage> rows) {
        return rows.subList(0, rows.size() - 1);
    }

    private static int compareUuidBytes(UUID left, UUID right) {
        int high = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    /**
     * The status names a channel page query compares the share status with.
     * The query takes them even without a status filter, because a native IN
     * list cannot be empty; the caller then passes every status and a flag
     * that skips the comparison.
     *
     * @param statuses statuses to keep, not empty
     * @return their names, in the given order
     */
    private static List<String> pullRequestStatusNames(List<PullRequestShareStatus> statuses) {
        return statuses.stream().map(PullRequestShareStatus::name).toList();
    }

    /**
     * The four keyset queries of one conversation, the channel or a thread.
     * Every query returns its rows oldest first.
     */
    private interface PageSource {

        List<ChatMessage> latest(int limit);

        List<ChatMessage> before(ChatMessage cursor, int limit);

        List<ChatMessage> after(ChatMessage cursor, int limit);

        List<ChatMessage> around(ChatMessage cursor, int olderLimit, int newerLimit);
    }
}
