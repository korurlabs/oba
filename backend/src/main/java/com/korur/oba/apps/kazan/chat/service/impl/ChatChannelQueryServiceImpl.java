package com.korur.oba.apps.kazan.chat.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kazan.chat.constant.service.ChatServiceLogConstant;
import com.korur.oba.apps.kazan.chat.dto.projection.ChatChannelMemberCountProjection;
import com.korur.oba.apps.kazan.chat.dto.response.ChatChannelResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kazan.chat.mapper.ChatChannelMapper;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelMemberRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Implementation of chat channel reads and of the channel access check.
 *
 * The channel list costs a fixed number of queries whatever the number of
 * channels: the channels, the reader's private memberships, the private
 * member counts and the organization's member count, the last three only
 * when there is something to count. An organization has few enough channels
 * that the list is filtered in memory.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatChannelQueryServiceImpl implements ChatChannelQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatChannelQueryServiceImpl.class);

    private final ChatChannelRepository chatChannelRepository;
    private final ChatChannelMemberRepository chatChannelMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatChannelMapper chatChannelMapper;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;

    @Override
    public List<ChatChannelResponse> listChannels(User user, UUID organizationId) {
        boolean admin = isOwnerOrAdmin(organizationId, user.getId());

        List<ChatChannel> channels = chatChannelRepository.findByOrganizationIdOrderByCreatedAtAscIdAsc(organizationId);
        List<UUID> privateIds = channels.stream()
                .filter(channel -> channel.getVisibility() == ChatChannelVisibility.PRIVATE)
                .map(ChatChannel::getId)
                .toList();
        Set<UUID> memberOf = privateIds.isEmpty()
                ? Set.of()
                : new HashSet<>(chatChannelMemberRepository.findChannelIdsByUserIdAndChannelIds(user.getId(),
                        privateIds));
        Map<UUID, Integer> privateCounts = countPrivateMembers(privateIds);
        boolean anyPublic = privateIds.size() < channels.size();
        int organizationCount = anyPublic ? (int) organizationMemberQueryService.countActiveMembers(organizationId) : 0;

        List<ChatChannelResponse> responses = channels.stream()
                .filter(channel -> channel.getVisibility() == ChatChannelVisibility.PUBLIC
                        || admin
                        || memberOf.contains(channel.getId()))
                .map(channel -> channel.getVisibility() == ChatChannelVisibility.PUBLIC
                        ? chatChannelMapper.toResponse(channel, true, organizationCount)
                        : chatChannelMapper.toResponse(channel, memberOf.contains(channel.getId()),
                                privateCounts.getOrDefault(channel.getId(), 0)))
                .toList();
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_CHANNELS_LISTED,
                user.getId(), organizationId, responses.size());
        return responses;
    }

    @Override
    public ChatChannel verifyChannelAccess(UUID organizationId, UUID userId, UUID channelId) {
        Optional<ChatChannel> channel = channelId == null
                ? Optional.empty()
                : chatChannelRepository.findByIdAndOrganizationId(channelId, organizationId);
        // A private channel the user is not on is reported exactly like a
        // missing one, so its existence is not revealed.
        return channel
                .filter(found -> found.getVisibility() == ChatChannelVisibility.PUBLIC
                        || chatChannelMemberRepository.existsByChannelIdAndUserId(found.getId(), userId))
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ChatExceptionLogConstant.CHAT_CHANNEL_NOT_FOUND,
                            channelId, organizationId, userId);
                    Map<String, Object> values = new HashMap<>();
                    values.put(ChatConstant.FIELD_CHANNEL_ID, channelId);
                    return new NotFoundException(ExceptionDomainKazanError.NOT_CHAT_CHANNEL_FOUND, values);
                });
    }

    @Override
    public boolean canRead(UUID organizationId, UUID userId, UUID channelId) {
        return chatChannelRepository.existsReadableChannel(organizationId, userId, channelId);
    }

    @Override
    public Optional<UUID> findDailyUpdateChannelId(UUID organizationId) {
        return chatChannelRepository.findDailyUpdateChannelId(organizationId);
    }

    @Override
    public Optional<UUID> findPullRequestChannelId(UUID organizationId) {
        return chatChannelRepository.findPullRequestChannelId(organizationId);
    }

    @Override
    public Map<UUID, ChatMessage> findPullRequestShareMessages(Collection<UUID> shareIds) {
        if (shareIds == null || shareIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ChatMessage> messagesByShareId = new HashMap<>();
        for (ChatMessage message : chatMessageRepository.findAllByPullRequestShareIdIn(shareIds)) {
            messagesByShareId.put(message.getPullRequestShareId(), message);
        }
        return messagesByShareId;
    }

    @Override
    public ChatMessage getMessageEntityById(UUID messageId) {
        return chatMessageRepository.findById(messageId)
                .orElseThrow(() -> {
                    // The organization is unknown until the message loads, so the log names none.
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ChatExceptionLogConstant.CHAT_MESSAGE_NOT_FOUND,
                            messageId, null);
                    return new NotFoundException(ExceptionDomainKazanError.NOT_CHAT_MESSAGE_FOUND,
                            Map.of(ChatConstant.FIELD_MESSAGE_ID, messageId));
                });
    }

    /**
     * Tells whether the user owns or administers the organization; those see
     * every private channel in the list, without reading its content.
     */
    private boolean isOwnerOrAdmin(UUID organizationId, UUID userId) {
        OrganizationMemberRole role = organizationMemberQueryService.findMember(organizationId, userId)
                .map(OrganizationMember::getRole)
                .orElse(null);
        return role == OrganizationMemberRole.OWNER || role == OrganizationMemberRole.ADMIN;
    }

    /**
     * The member list sizes of the private channels; a channel without
     * members is absent and counts 0.
     */
    private Map<UUID, Integer> countPrivateMembers(List<UUID> privateIds) {
        if (privateIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> counts = new HashMap<>();
        for (ChatChannelMemberCountProjection row : chatChannelMemberRepository.countMembersByChannelIds(privateIds)) {
            counts.put(row.getChannelId(), row.getMemberCount() == null ? 0 : row.getMemberCount());
        }
        return counts;
    }
}
