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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMemberResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kazan.chat.mapper.ChatMessageMapper;
import com.korur.oba.apps.kazan.chat.repository.ChatChannelMemberRepository;
import com.korur.oba.apps.kazan.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatMemberQueryService;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberKindSummaryProjection;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Implementation of the channel member list.
 *
 * The members come from one projection query of the organization domain,
 * which already keeps only ACTIVE memberships held by ACTIVE accounts, the
 * same set a mention is checked against, so the list never offers a user the
 * server would refuse. Agents are left out, so the list holds only the
 * people of the organization. Online state is the realtime registry's in-memory
 * snapshot and costs no query. An organization has few enough members that
 * the search and the ordering run in memory. With a channel the list narrows
 * after the channel check: to the member list of a private channel, or to
 * the authors of the channel's live messages when only authors are asked for.
 * Whether each listed member may open Kazan is read for the whole list with
 * one query, so the mention list can show a member it cannot mention.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMemberQueryServiceImpl implements ChatMemberQueryService {

    /**
     * Online members first, then by name, then by email; the user ID settles
     * ties, so the order is the same on every call.
     */
    private static final Comparator<ChatMemberResponse> MEMBER_ORDER = Comparator
            .comparing(ChatMemberResponse::online, Comparator.reverseOrder())
            .thenComparing(member -> normalize(member.firstName()))
            .thenComparing(member -> normalize(member.lastName()))
            .thenComparing(member -> normalize(member.email()))
            .thenComparing(ChatMemberResponse::userId);

    private final ChatChannelMemberRepository chatChannelMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatChannelQueryService chatChannelQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final ChatMessageMapper chatMessageMapper;

    @Override
    public List<ChatMemberResponse> listMembers(User user, UUID organizationId, String search, UUID channelId,
                                                boolean authorsOnly) {
        Set<UUID> allowed = resolveListedUserIds(user, organizationId, channelId, authorsOnly);

        String needle = normalize(search);
        Set<UUID> online = realtimeSessionRegistry.onlineUserIds(organizationId);
        List<OrganizationMemberKindSummaryProjection> members = new ArrayList<>();
        List<UUID> memberIds = new ArrayList<>();
        for (OrganizationMemberKindSummaryProjection member
                : organizationMemberQueryService.listActiveMemberKindSummaries(organizationId, Instant.now())) {
            if (UserKind.AGENT.name().equals(member.getKind())) {
                continue;
            }
            if (allowed != null && !allowed.contains(member.getUserId())) {
                continue;
            }
            if (!needle.isEmpty() && !matches(member, needle)) {
                continue;
            }
            members.add(member);
            memberIds.add(member.getUserId());
        }
        Set<UUID> kazanUserIds = appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.KAZAN,
                memberIds);
        List<ChatMemberResponse> responses = new ArrayList<>();
        for (OrganizationMemberKindSummaryProjection member : members) {
            responses.add(chatMessageMapper.toMemberResponse(member, online.contains(member.getUserId()),
                    kazanUserIds.contains(member.getUserId())));
        }
        responses.sort(MEMBER_ORDER);
        return responses;
    }

    /**
     * Checks the reader's access to a named channel and returns the users
     * the list is narrowed to: the authors of the channel's live messages
     * when only authors are asked for, the member list of a private channel,
     * or null for every active member of the organization.
     */
    private Set<UUID> resolveListedUserIds(User user, UUID organizationId, UUID channelId, boolean authorsOnly) {
        if (channelId == null) {
            return null;
        }
        ChatChannel channel = chatChannelQueryService.verifyChannelAccess(organizationId, user.getId(), channelId);
        if (authorsOnly) {
            return new HashSet<>(chatMessageRepository.findAuthorIdsByChannelId(channel.getId()));
        }
        if (channel.getVisibility() == ChatChannelVisibility.PRIVATE) {
            return new HashSet<>(chatChannelMemberRepository.findUserIdsByChannelId(channel.getId()));
        }
        return null;
    }

    /**
     * Tells whether the search text occurs in the member's first name, last
     * name, full name or email.
     */
    private static boolean matches(OrganizationMemberSummaryProjection member, String needle) {
        String firstName = normalize(member.getFirstName());
        String lastName = normalize(member.getLastName());
        String fullName = firstName + ChatConstant.MEMBER_NAME_SEPARATOR + lastName;
        return fullName.contains(needle) || normalize(member.getEmail()).contains(needle);
    }

    /**
     * Trims and lower-cases text for matching and ordering; null becomes empty.
     */
    private static String normalize(String text) {
        return text == null ? ChatConstant.EMPTY_TEXT : text.trim().toLowerCase(Locale.ROOT);
    }
}
