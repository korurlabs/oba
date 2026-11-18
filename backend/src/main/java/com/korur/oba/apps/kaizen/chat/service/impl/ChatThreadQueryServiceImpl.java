package com.korur.oba.apps.kaizen.chat.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadPageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatChannel;
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;
import com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus;
import com.korur.oba.apps.kaizen.chat.mapper.ChatThreadMapperHelper;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadRepository;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.chat.service.ChatThreadQueryService;
import com.korur.oba.apps.kaizen.chat.validator.ChatValidator;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of chat thread read operations.
 *
 * The thread list asks for one row more than the page holds; the extra row
 * only tells whether more threads follow and is dropped. The cursor is the
 * last thread of the previous page, read again for its current activity
 * time. When that thread got a reply meanwhile, the next page starts from its
 * new place and may repeat threads the client already holds, which it keys
 * by ID.
 *
 * Every read checks the channel first: the request's channel for the list,
 * the thread's channel for one thread. The list cursor must be a thread of
 * the same channel.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatThreadQueryServiceImpl implements ChatThreadQueryService {

    private final ChatThreadRepository chatThreadRepository;
    private final ChatValidator chatValidator;
    private final ChatThreadMapperHelper chatThreadMapperHelper;
    private final ChatChannelQueryService chatChannelQueryService;

    @Override
    public ChatThreadPageResponse listThreads(User user, UUID organizationId, UUID channelId,
                                              ChatThreadStatus status, UUID before, Integer limit) {
        ChatChannel channel = chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), channelId);
        int pageSize = limit == null ? ChatConstant.DEFAULT_PAGE_SIZE : limit;

        List<ChatThread> rows;
        if (before == null) {
            rows = chatThreadRepository.findLatestThreads(channel.getId(), status.name(), pageSize + 1);
        } else {
            ChatThread cursor = chatValidator.ensureThreadExists(organizationId, before);
            chatValidator.ensureThreadInChannel(cursor, channel.getId());
            rows = chatThreadRepository.findThreadsBefore(channel.getId(), status.name(), cursor.getLastMessageAt(),
                    cursor.getId(), pageSize + 1);
        }
        boolean hasMore = rows.size() > pageSize;
        List<ChatThread> page = hasMore ? rows.subList(0, pageSize) : rows;
        return chatThreadMapperHelper.toPageResponse(page, hasMore, user, organizationId);
    }

    @Override
    public ChatThreadResponse getThread(User user, UUID organizationId, UUID threadId) {
        ChatThread thread = chatValidator.ensureThreadExists(organizationId, threadId);
        chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), thread.getChannelId());
        return chatThreadMapperHelper.toResponse(thread, user, organizationId);
    }
}
