package com.korur.oba.apps.kazan.chat.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.projection.ChatUnreadCountProjection;
import com.korur.oba.apps.kazan.chat.dto.response.ChatConversationUnreadResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatUnreadResponse;
import com.korur.oba.apps.kazan.chat.mapper.ChatReadStateMapper;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.service.ChatReadStateQueryService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of unread count reads.
 *
 * Every count comes from one native query over the channel and thread
 * indexes, also for many users at once, so the realtime fan-out after a
 * message costs one query whatever the number of online members. The query
 * works out each user's readable channels itself, so a private channel
 * counts only for its members; it already caps each count and drops closed
 * and archived threads and threads without unread messages.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatReadStateQueryServiceImpl implements ChatReadStateQueryService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStateMapper chatReadStateMapper;

    @Override
    public ChatUnreadResponse getUnread(User user, UUID organizationId) {
        return toResponse(chatMessageRepository.findUnreadCounts(user.getId(), organizationId));
    }

    @Override
    public Map<UUID, ChatUnreadResponse> getUnreadByUsers(UUID organizationId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ChatUnreadCountProjection>> rowsByUser = new LinkedHashMap<>();
        for (ChatUnreadCountProjection row : chatMessageRepository.findUnreadCountsByUsers(organizationId,
                new LinkedHashSet<>(userIds))) {
            rowsByUser.computeIfAbsent(row.getUserId(), userId -> new ArrayList<>()).add(row);
        }
        Map<UUID, ChatUnreadResponse> unreadByUser = new LinkedHashMap<>();
        rowsByUser.forEach((userId, rows) -> unreadByUser.put(userId, toResponse(rows)));
        return unreadByUser;
    }

    /**
     * Builds one user's counts from that user's rows: one row per readable
     * channel (none when the membership is gone) and the thread rows. Both totals add up the
     * capped counts, so they can exceed 100.
     */
    private ChatUnreadResponse toResponse(List<ChatUnreadCountProjection> rows) {
        List<ChatConversationUnreadResponse> channels = new ArrayList<>();
        List<ChatConversationUnreadResponse> threads = new ArrayList<>();
        int total = 0;
        int mentionTotal = 0;
        for (ChatUnreadCountProjection row : rows) {
            if (row.getThreadId() == null) {
                channels.add(chatReadStateMapper.toChannelUnreadResponse(row));
            } else {
                threads.add(chatReadStateMapper.toThreadUnreadResponse(row));
            }
            total += row.getUnread() == null ? 0 : row.getUnread();
            mentionTotal += row.getMentionCount() == null ? 0 : row.getMentionCount();
        }
        return chatReadStateMapper.toUnreadResponse(channels, threads, total, mentionTotal);
    }
}
