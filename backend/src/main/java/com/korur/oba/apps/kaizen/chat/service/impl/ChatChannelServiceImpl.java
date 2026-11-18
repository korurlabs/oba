package com.korur.oba.apps.kaizen.chat.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.constant.ChatExceptionLogConstant;
import com.korur.oba.apps.kaizen.chat.constant.service.ChatServiceLogConstant;
import com.korur.oba.apps.kaizen.chat.dto.projection.ChatChannelMemberCountProjection;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatChannelCreateRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatChannelMembersRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatChannelUpdateRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatChannelResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatChannel;
import com.korur.oba.apps.kaizen.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kaizen.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kaizen.chat.event.ChatChannelChangedEvent;
import com.korur.oba.apps.kaizen.chat.mapper.ChatChannelMapper;
import com.korur.oba.apps.kaizen.chat.repository.ChatChannelMemberRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatChannelRepository;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelService;
import com.korur.oba.apps.kaizen.chat.validator.ChatValidator;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.SlugUtil;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.event.OrganizationCreatedEvent;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Implementation of chat channel write operations.
 *
 * Owners and admins find a channel by its organization alone, because they
 * manage private channels they are not on. A member leaving a channel goes
 * through the channel access check instead, so a private channel the member
 * cannot read is reported as not found. Member rows are written with an
 * insert that ignores existing members, so two admins adding the same user at
 * once both succeed.
 *
 * A purpose other than GENERAL is unique among the live channels of an
 * organization. A channel asking for such a purpose while another channel
 * holds it is refused; the admin sets that channel to GENERAL first. The
 * unique index chat_channels_purpose_uk is the final guard when two requests
 * take the purpose at once. Events are published inside the transaction and
 * consumed after the commit.
 *
 * Every channel has a slug, its URL segment in the web app, built from the
 * name and unique among the live channels of the organization; a collision
 * gets the first free numeric suffix. A rename keeps the slug while the new
 * name has the same base slug, so a change of letter case or punctuation
 * does not move the channel's URL. The unique index
 * chat_channels_organization_id_slug_uk refuses a slug a concurrent write
 * took after the check.
 */
@Service
@RequiredArgsConstructor
public class ChatChannelServiceImpl implements ChatChannelService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ChatChannelServiceImpl.class);

    private final ChatChannelRepository chatChannelRepository;
    private final ChatChannelMemberRepository chatChannelMemberRepository;
    private final ChatChannelQueryService chatChannelQueryService;
    private final ChatChannelMapper chatChannelMapper;
    private final ChatValidator chatValidator;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessValidator appAccessValidator;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public ChatChannelResponse createChannel(User user, UUID organizationId, ChatChannelCreateRequest request) {
        chatValidator.ensureAutoPostChannelPublic(null, request.visibility(), request.purpose());
        chatValidator.ensureChannelMembersExpected(null, request.visibility(), request.memberIds());
        ensureMembersActive(organizationId, request.memberIds());
        if (request.purpose() != ChatChannelPurpose.GENERAL) {
            ensurePurposeFree(organizationId, null, request.purpose());
        }

        ChatChannel newChannel = chatChannelMapper.toEntity(request);
        newChannel.setOrganizationId(organizationId);
        newChannel.setCreatedBy(user.getId());
        newChannel.setSlug(nextFreeSlug(organizationId, newChannel.getName(), null));
        // Flushed at once: the member rows below refer to the stored channel.
        ChatChannel channel = saveChannel(newChannel, organizationId);

        int memberCount = 0;
        if (channel.getVisibility() == ChatChannelVisibility.PRIVATE) {
            Set<UUID> members = withAdmin(user.getId(), request.memberIds());
            insertMembers(channel.getId(), members);
            memberCount = members.size();
        }

        publishChannelEvent(ChatChannelChangedEvent.Type.CREATED, channel, List.of());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_CHANNEL_CREATED,
                channel.getId(), organizationId, channel.getVisibility(), channel.getPurpose(), memberCount);

        return toResponse(channel, user.getId(), organizationId);
    }

    @Override
    @Transactional
    public ChatChannelResponse updateChannel(User user, UUID organizationId, UUID channelId,
                                             ChatChannelUpdateRequest request) {
        ChatChannel channel = requireChannel(organizationId, user.getId(), channelId);
        // A full update names the purpose again, so this also refuses making
        // a channel with a purpose other than GENERAL private while it keeps it.
        chatValidator.ensureAutoPostChannelPublic(channelId, request.visibility(), request.purpose());
        chatValidator.ensureChannelMembersExpected(channel, request.visibility(), request.memberIds());

        ChatChannelVisibility previousVisibility = channel.getVisibility();
        boolean becomesPrivate = previousVisibility == ChatChannelVisibility.PUBLIC
                && request.visibility() == ChatChannelVisibility.PRIVATE;
        boolean becomesPublic = previousVisibility == ChatChannelVisibility.PRIVATE
                && request.visibility() == ChatChannelVisibility.PUBLIC;
        if (becomesPrivate) {
            ensureMembersActive(organizationId, request.memberIds());
        }

        // The channel that already holds the purpose keeps it without a lookup.
        if (request.purpose() != ChatChannelPurpose.GENERAL && channel.getPurpose() != request.purpose()) {
            ensurePurposeFree(organizationId, channelId, request.purpose());
        }

        chatChannelMapper.updateEntity(channel, request);
        syncSlugWithName(channel);
        channel = saveChannel(channel, organizationId);

        if (becomesPrivate) {
            // The users who lose the channel are everyone not on the new
            // list; the realtime layer works them out from this list.
            Set<UUID> members = withAdmin(user.getId(), request.memberIds());
            insertMembers(channelId, members);
            publishVisibilityChanged(channel, previousVisibility, new ArrayList<>(members));
        } else if (becomesPublic) {
            List<UUID> formerMembers = chatChannelMemberRepository.findUserIdsByChannelId(channelId);
            chatChannelMemberRepository.deleteAllByChannelId(channelId);
            publishVisibilityChanged(channel, previousVisibility, formerMembers);
        } else {
            publishChannelEvent(ChatChannelChangedEvent.Type.UPDATED, channel, List.of());
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_CHANNEL_UPDATED,
                channelId, channel.getVisibility(), channel.getPurpose());

        return toResponse(channel, user.getId(), organizationId);
    }

    @Override
    @Transactional
    public void deleteChannel(User user, UUID organizationId, UUID channelId) {
        ChatChannel channel = requireChannel(organizationId, user.getId(), channelId);

        // Soft delete: messages, threads and member rows stay, hidden with
        // the channel.
        chatChannelRepository.delete(channel);

        publishChannelEvent(ChatChannelChangedEvent.Type.DELETED, channel, List.of());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_CHANNEL_DELETED,
                channelId, user.getId());
    }

    @Override
    @Transactional
    public ChatChannelResponse addMembers(User user, UUID organizationId, UUID channelId,
                                          ChatChannelMembersRequest request) {
        ChatChannel channel = requireChannel(organizationId, user.getId(), channelId);
        chatValidator.ensureChannelPrivate(channel);
        ensureMembersActive(organizationId, request.userIds());

        List<UUID> added = insertMembers(channelId, new LinkedHashSet<>(request.userIds()));
        if (!added.isEmpty()) {
            publishChannelEvent(ChatChannelChangedEvent.Type.MEMBERS_ADDED, channel, added);
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_CHANNEL_MEMBERS_ADDED,
                    channelId, added);
        }

        return toResponse(channel, user.getId(), organizationId);
    }

    @Override
    @Transactional
    public void removeMember(User user, UUID organizationId, UUID channelId, UUID userId) {
        ChatChannel channel;
        if (user.getId().equals(userId)) {
            // Leaving needs only the right to read the channel, so a member
            // of any role may leave, and a private channel the caller is not
            // on stays hidden.
            channel = chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), channelId);
        } else {
            ensureCanRemoveOthers(organizationId, user, channelId, userId);
            channel = requireChannel(organizationId, user.getId(), channelId);
        }
        chatValidator.ensureChannelPrivate(channel);

        if (chatChannelMemberRepository.deleteByChannelIdAndUserId(channelId, userId) == 0) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE,
                    ChatServiceLogConstant.CHAT_CHANNEL_MEMBER_REMOVE_UNCHANGED, channelId, userId);
            return;
        }

        publishChannelEvent(ChatChannelChangedEvent.Type.MEMBERS_REMOVED, channel, List.of(userId));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_CHANNEL_MEMBER_REMOVED,
                channelId, userId, user.getId());
    }

    @Override
    @EventListener
    @Transactional
    public void onOrganizationCreated(OrganizationCreatedEvent event) {
        int created = chatChannelRepository.insertInitialChannelsIfAbsent(event.organizationId());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_INITIAL_CHANNELS_CREATED,
                event.organizationId(), created);
    }

    /**
     * Ensures the caller may remove another user from a member list: an
     * owner or admin of the organization. The interceptor already checked
     * membership, its status and the organization for this request, so only
     * the role is read here.
     */
    private void ensureCanRemoveOthers(UUID organizationId, User user, UUID channelId, UUID memberId) {
        OrganizationMemberRole role = organizationMemberQueryService.findMember(organizationId, user.getId())
                .map(OrganizationMember::getRole)
                .orElse(null);
        if (role == null || !role.isAtLeast(OrganizationMemberRole.ADMIN)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    ChatExceptionLogConstant.CHAT_CHANNEL_MEMBER_REMOVE_NOT_ALLOWED,
                    channelId, memberId, user.getId(), role);
            throw new ForbiddenException(ExceptionDomainKaizenError.SEC_CANNOT_REMOVE_CHANNEL_MEMBER,
                    Map.of(ChatConstant.FIELD_CHANNEL_ID, channelId));
        }
    }

    /**
     * Finds a live channel of the organization for an owner or admin, who
     * manages private channels without being on them.
     */
    private ChatChannel requireChannel(UUID organizationId, UUID userId, UUID channelId) {
        return chatChannelRepository.findByIdAndOrganizationId(channelId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ChatExceptionLogConstant.CHAT_CHANNEL_NOT_FOUND,
                            channelId, organizationId, userId);
                    Map<String, Object> values = new HashMap<>();
                    values.put(ChatConstant.FIELD_CHANNEL_ID, channelId);
                    return new NotFoundException(ExceptionDomainKaizenError.NOT_CHAT_CHANNEL_FOUND, values);
                });
    }

    /**
     * Ensures every user to put on a member list is an active member of the
     * organization who may open Kaizen there; the members are read only when
     * users are named.
     */
    private void ensureMembersActive(UUID organizationId, List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<UUID> activeMemberIds = organizationMemberQueryService.findActiveUserIdsByRoles(organizationId,
                EnumSet.allOf(OrganizationMemberRole.class));
        chatValidator.ensureChannelMembersActive(organizationId, userIds, activeMemberIds);
        appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.KAIZEN, userIds);
    }

    /**
     * Refuses a purpose other than GENERAL while another live channel of the
     * organization holds it.
     */
    private void ensurePurposeFree(UUID organizationId, UUID channelId, ChatChannelPurpose purpose) {
        UUID holderId = chatChannelRepository.findChannelIdByPurpose(organizationId, purpose.name()).orElse(null);
        chatValidator.ensurePurposeFree(organizationId, channelId, purpose, holderId);
    }

    /**
     * Picks the slug a channel with the given name gets in its organization:
     * the name's base slug, or the first free numeric suffix after it.
     *
     * @param ignoreChannelId the channel whose own slug does not count as taken, or null on create
     */
    private String nextFreeSlug(UUID organizationId, String name, UUID ignoreChannelId) {
        String base = SlugUtil.slugify(name, ChatConstant.CHANNEL_SLUG_FALLBACK);
        return SlugUtil.firstFree(base, candidate -> ignoreChannelId == null
                ? chatChannelRepository.existsByOrganizationIdAndSlug(organizationId, candidate)
                : chatChannelRepository.existsByOrganizationIdAndSlugAndIdNot(organizationId, candidate,
                        ignoreChannelId));
    }

    /**
     * Brings a channel's slug in line with its name after a rename. A name
     * whose base slug is still the base of the current slug keeps the slug;
     * any other name gets the next free slug of the organization.
     */
    private void syncSlugWithName(ChatChannel channel) {
        String base = SlugUtil.slugify(channel.getName(), ChatConstant.CHANNEL_SLUG_FALLBACK);
        if (SlugUtil.sameBase(channel.getSlug(), base)) {
            return;
        }
        channel.setSlug(nextFreeSlug(channel.getOrganizationId(), channel.getName(), channel.getId()));
    }

    /**
     * Writes a channel at once, so the member rows can refer to it and a
     * concurrent request that took the same purpose or slug after the check is
     * caught here. The unique purpose index is answered with the conflict the
     * purpose check gives, naming the purpose the channel asked for; the
     * unique slug index with a conflict the client can retry, naming the
     * channel name the slug was built from. Every other integrity failure goes
     * up as it is.
     *
     * @throws ConflictException STA_CHAT_PURPOSE_TAKEN when the purpose index refuses the write,
     *                           STA_SLUG_TAKEN when the slug index refuses it
     */
    private ChatChannel saveChannel(ChatChannel channel, UUID organizationId) {
        try {
            return chatChannelRepository.saveAndFlush(channel);
        } catch (DataIntegrityViolationException exception) {
            if (isPurposeConflict(exception)) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                        ChatExceptionLogConstant.CHAT_CHANNEL_PURPOSE_RACE_LOST, channel.getPurpose(), channel.getId(), organizationId, exception.getMostSpecificCause().getMessage());
                throw new ConflictException(ExceptionDomainKaizenError.STA_CHAT_PURPOSE_TAKEN,
                        Map.of(ChatConstant.FIELD_PURPOSE, channel.getPurpose().name()));
            }
            if (SlugUtil.isSlugConflict(exception, ChatConstant.CHANNEL_SLUG_UNIQUE_INDEX)) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                        ChatExceptionLogConstant.CHAT_CHANNEL_SLUG_RACE_LOST, channel.getSlug(), channel.getId(),
                        organizationId, exception.getMostSpecificCause().getMessage());
                throw new ConflictException(ExceptionDomainKaizenError.STA_SLUG_TAKEN,
                        Map.of(ChatConstant.FIELD_NAME, channel.getName()));
            }
            throw exception;
        }
    }

    /**
     * Tells whether an integrity failure is the unique index on organization
     * and purpose, and not any other constraint of the channel.
     */
    private static boolean isPurposeConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return ChatConstant.CHANNEL_PURPOSE_UNIQUE_INDEX.equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * The members of a channel turning private: the admin first, then the
     * named users, each once.
     */
    private static Set<UUID> withAdmin(UUID adminId, List<UUID> memberIds) {
        Set<UUID> members = new LinkedHashSet<>();
        members.add(adminId);
        if (memberIds != null) {
            members.addAll(memberIds);
        }
        return members;
    }

    /**
     * Puts users on a channel's member list.
     *
     * @return the users who were not on it before
     */
    private List<UUID> insertMembers(UUID channelId, Set<UUID> userIds) {
        List<UUID> added = new ArrayList<>();
        for (UUID userId : userIds) {
            if (chatChannelMemberRepository.insertIfAbsent(channelId, userId) > 0) {
                added.add(userId);
            }
        }
        return added;
    }

    private void publishVisibilityChanged(ChatChannel channel, ChatChannelVisibility previousVisibility,
                                          List<UUID> affectedUserIds) {
        publishChannelEvent(ChatChannelChangedEvent.Type.VISIBILITY_CHANGED, channel, affectedUserIds);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_CHANNEL_VISIBILITY_CHANGED,
                channel.getId(), previousVisibility, channel.getVisibility());
    }

    private void publishChannelEvent(ChatChannelChangedEvent.Type type, ChatChannel channel,
                                     List<UUID> affectedUserIds) {
        applicationEventPublisher.publishEvent(new ChatChannelChangedEvent(type, channel.getOrganizationId(),
                channel.getId(), channel.getVisibility(), List.copyOf(affectedUserIds)));
    }

    /**
     * Maps a channel for the admin or member who changed it: a public
     * channel counts the organization's active members, a private one its
     * member list.
     */
    private ChatChannelResponse toResponse(ChatChannel channel, UUID userId, UUID organizationId) {
        if (channel.getVisibility() == ChatChannelVisibility.PUBLIC) {
            int organizationCount = (int) organizationMemberQueryService.countActiveMembers(organizationId);
            return chatChannelMapper.toResponse(channel, true, organizationCount);
        }
        int memberCount = chatChannelMemberRepository.countMembersByChannelIds(List.of(channel.getId())).stream()
                .map(ChatChannelMemberCountProjection::getMemberCount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0);
        boolean member = chatChannelMemberRepository.existsByChannelIdAndUserId(channel.getId(), userId);
        return chatChannelMapper.toResponse(channel, member, memberCount);
    }
}
