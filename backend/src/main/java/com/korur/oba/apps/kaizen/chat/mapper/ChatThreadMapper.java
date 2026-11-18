package com.korur.oba.apps.kaizen.chat.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK
import java.time.Instant;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.request.ChatThreadCreateRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatThreadUpdateRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadPageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatUserResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;
import com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus;

/**
 * Maps chat threads between requests, entities and responses.
 *
 * The organization, creator, title and activity time of a new thread are set
 * by {@link ChatThreadMapperHelper}; the per-reader parts of a response (root
 * message, effective status, membership, members, canManage) are computed
 * there and passed in.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ChatThreadMapper {

    /**
     * Maps a create request to a new thread. The title is set by
     * {@link ChatThreadMapperHelper}, because a null title takes a default derived
     * from the root message; message count and auto-archive window keep
     * their entity defaults.
     *
     * @param request the create request
     * @return the new thread without relations
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "rootMessageId", source = "rootMessageId")
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "channelId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastMessageAt", ignore = true)
    @Mapping(target = "messageCount", ignore = true)
    @Mapping(target = "autoArchiveMinutes", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "statusChangedAt", ignore = true)
    @Mapping(target = "statusChangedBy", ignore = true)
    ChatThread toEntity(ChatThreadCreateRequest request);

    /**
     * Applies a full update: title and auto-archive window. The status has
     * a request and a statement of its own,
     * {@link com.korur.oba.apps.kaizen.chat.repository.ChatThreadRepository#updateStatus}.
     *
     * @param thread  the stored thread
     * @param request the update request
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "autoArchiveMinutes", source = "autoArchiveMinutes")
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "channelId", ignore = true)
    @Mapping(target = "rootMessageId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastMessageAt", ignore = true)
    @Mapping(target = "messageCount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "statusChangedAt", ignore = true)
    @Mapping(target = "statusChangedBy", ignore = true)
    void updateEntity(@MappingTarget ChatThread thread, ChatThreadUpdateRequest request);

    /**
     * Maps a thread to its response for one reader.
     *
     * @param thread          the thread
     * @param rootMessage     the root message, or its placeholder when it was deleted
     * @param effectiveStatus the status the reader sees, from
     *                        {@link com.korur.oba.apps.kaizen.chat.helper.ChatThreadStatusResolver}
     * @param effectiveSince  when that status took effect; null when the status was never changed
     * @param statusChanger   who set that status; null when nobody did
     * @param member          true when the reader is a member
     * @param members         the members to show
     * @param canManage       true when the reader may rename the thread and change its window and status
     * @return the response
     */
    @Mapping(target = "id", source = "thread.id")
    @Mapping(target = "channelId", source = "thread.channelId")
    @Mapping(target = "rootMessageId", source = "thread.rootMessageId")
    @Mapping(target = "rootMessage", source = "rootMessage")
    @Mapping(target = "title", source = "thread.title")
    @Mapping(target = "createdBy", source = "thread.createdBy")
    @Mapping(target = "messageCount", source = "thread.messageCount")
    @Mapping(target = "lastMessageAt", source = "thread.lastMessageAt")
    @Mapping(target = "autoArchiveMinutes", source = "thread.autoArchiveMinutes")
    @Mapping(target = "status", source = "effectiveStatus")
    @Mapping(target = "statusChangedAt", source = "effectiveSince")
    @Mapping(target = "statusChangedBy", source = "statusChanger")
    @Mapping(target = "member", source = "member")
    @Mapping(target = "members", source = "members")
    @Mapping(target = "canManage", source = "canManage")
    ChatThreadResponse toResponse(ChatThread thread, ChatMessageResponse rootMessage,
                                  ChatThreadStatus effectiveStatus, Instant effectiveSince,
                                  ChatUserResponse statusChanger, boolean member, List<ChatUserResponse> members,
                                  boolean canManage);

    /**
     * Maps a keyset page of threads.
     *
     * @param items   the threads of the page
     * @param hasMore true when more threads exist after the last item
     * @return the page response
     */
    ChatThreadPageResponse toPageResponse(List<ChatThreadResponse> items, boolean hasMore);
}
