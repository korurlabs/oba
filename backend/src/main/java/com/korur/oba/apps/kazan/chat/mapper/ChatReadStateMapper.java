package com.korur.oba.apps.kazan.chat.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.projection.ChatUnreadCountProjection;
import com.korur.oba.apps.kazan.chat.dto.response.ChatConversationUnreadResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatUnreadResponse;

/**
 * Maps the rows of the unread query to a user's unread counts.
 *
 * The query returns one row per readable channel and one row per thread with
 * unread messages; {@link com.korur.oba.apps.kazan.chat.service.impl.ChatReadStateQueryServiceImpl} splits them and adds up the
 * totals, then passes the parts in.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ChatReadStateMapper {

    /**
     * Maps a channel row to its unread count; the channel is its own channelId.
     *
     * @param row a row of the unread query without a thread ID
     * @return the channel's unread count and read position
     */
    @Mapping(target = "id", source = "channelId")
    @Mapping(target = "channelId", source = "channelId")
    @Mapping(target = "unread", source = "unread", defaultValue = "0")
    @Mapping(target = "mentionCount", source = "mentionCount", defaultValue = "0")
    @Mapping(target = "lastReadMessageId", source = "lastReadMessageId")
    @Mapping(target = "lastReadAt", source = "lastReadAt")
    ChatConversationUnreadResponse toChannelUnreadResponse(ChatUnreadCountProjection row);

    /**
     * Maps a thread row to its unread count, with the thread's channel.
     *
     * @param row a row of the unread query with a thread ID
     * @return the thread's unread count and read position
     */
    @Mapping(target = "id", source = "threadId")
    @Mapping(target = "channelId", source = "channelId")
    @Mapping(target = "unread", source = "unread", defaultValue = "0")
    @Mapping(target = "mentionCount", source = "mentionCount", defaultValue = "0")
    @Mapping(target = "lastReadMessageId", source = "lastReadMessageId")
    @Mapping(target = "lastReadAt", source = "lastReadAt")
    ChatConversationUnreadResponse toThreadUnreadResponse(ChatUnreadCountProjection row);

    /**
     * Maps a user's unread counts.
     *
     * @param channels     the user's channel counts
     * @param threads      the user's thread counts
     * @param total        every channel count plus every thread count
     * @param mentionTotal every channel notification count plus every thread notification count
     * @return the unread counts
     */
    @Mapping(target = "channels", source = "channels")
    @Mapping(target = "threads", source = "threads")
    @Mapping(target = "total", source = "total")
    @Mapping(target = "mentionTotal", source = "mentionTotal")
    ChatUnreadResponse toUnreadResponse(List<ChatConversationUnreadResponse> channels,
                                        List<ChatConversationUnreadResponse> threads, int total, int mentionTotal);
}
