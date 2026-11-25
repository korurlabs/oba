package com.korur.oba.apps.kazan.dailyupdate.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.dailyupdate.constant.DailyUpdateConstant;
import com.korur.oba.apps.kazan.dailyupdate.dto.projection.DailyUpdateFirstPostProjection;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateMissingRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdatePreviewRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateQueryRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateTicketRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateMissingMemberResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateMissingResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdatePreviewResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdateTicket;
import com.korur.oba.apps.kazan.dailyupdate.helper.DailyUpdateDeadlineResolver;
import com.korur.oba.apps.kazan.dailyupdate.helper.DailyUpdateDeadlineResolver.ReportDay;
import com.korur.oba.apps.kazan.dailyupdate.mapper.DailyUpdateMapper;
import com.korur.oba.apps.kazan.dailyupdate.mapper.DailyUpdateMapperHelper;
import com.korur.oba.apps.kazan.dailyupdate.repository.DailyUpdateRepository;
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kazan.dailyupdate.specification.DailyUpdateSpecification;
import com.korur.oba.apps.kazan.dailyupdate.validator.DailyUpdateValidator;
import com.korur.oba.apps.kazan.ticket.dto.response.AllTicketListResponse;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.dojo.time.BusinessCalendar;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberKindSummaryProjection;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Implementation of daily update read operations.
 *
 * All methods run within a read-only transaction. Previews build detached
 * lines and never change the stored update, so nothing is flushed.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyUpdateQueryServiceImpl implements DailyUpdateQueryService {

    /**
     * Case-insensitive name order; a missing first or last name sorts last.
     */
    private static final Comparator<String> NAME_ORDER = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);

    /**
     * Order of the not-posted list: members who have not posted first, by
     * first name, last name and email; then late posters by posting time.
     */
    private static final Comparator<DailyUpdateMissingMemberResponse> MISSING_MEMBER_ORDER =
            Comparator.comparing(DailyUpdateMissingMemberResponse::postedAt,
                            Comparator.nullsFirst(Comparator.<Instant>naturalOrder()))
                    .thenComparing(DailyUpdateMissingMemberResponse::firstName, NAME_ORDER)
                    .thenComparing(DailyUpdateMissingMemberResponse::lastName, NAME_ORDER)
                    .thenComparing(DailyUpdateMissingMemberResponse::email, NAME_ORDER);

    private final DailyUpdateRepository dailyUpdateRepository;
    private final DailyUpdateValidator dailyUpdateValidator;
    private final DailyUpdateMapper dailyUpdateMapper;
    private final DailyUpdateMapperHelper dailyUpdateMapperHelper;
    private final DailyUpdateDeadlineResolver dailyUpdateDeadlineResolver;
    private final TicketQueryService ticketQueryService;
    private final BoardMemberQueryService boardMemberQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final OrganizationAccessService organizationAccessService;
    private final AppAccessQueryService appAccessQueryService;
    private final BusinessCalendar businessCalendar;

    @Override
    public DailyUpdatePreviewResponse previewDailyUpdate(User user, UUID organizationId,
                                                         DailyUpdatePreviewRequest request) {
        List<DailyUpdateTicketRequest> lines = request.tickets();
        dailyUpdateValidator.ensureWithinLimits(lines, request.focus(), request.questions());
        dailyUpdateValidator.ensureNoDuplicateTickets(lines);

        UUID postingOrganizationId = resolvePostingOrganizationId(user, organizationId);
        Map<UUID, Ticket> ticketsById = dailyUpdateMapperHelper.resolveAccessibleTickets(user.getId(),
                postingOrganizationId, lines.stream().map(DailyUpdateTicketRequest::ticketId).toList());
        Set<UUID> syncTicketIds = findProgressSyncTicketIds(user, postingOrganizationId, lines, ticketsById);

        List<DailyUpdateTicket> items = dailyUpdateMapperHelper.buildItems(lines, ticketsById);
        markProgressSynced(items, syncTicketIds);
        return dailyUpdateMapperHelper.toPreviewResponse(items, request.focus(), request.questions(),
                postingOrganizationId, user.getId());
    }

    @Override
    public DailyUpdatePreviewResponse previewDailyUpdateEdit(User user, UUID updateId,
                                                             DailyUpdatePreviewRequest request) {
        DailyUpdate dailyUpdate = dailyUpdateValidator.ensureDailyUpdateVisible(
                organizationMemberQueryService.getColleagueUserIds(user.getId()), updateId);
        dailyUpdateValidator.ensureAuthor(dailyUpdate, user);
        List<DailyUpdateTicketRequest> lines = request.tickets();
        dailyUpdateValidator.ensureWithinLimits(lines, request.focus(), request.questions());
        dailyUpdateValidator.ensureNoDuplicateTickets(lines);

        UUID ticketOrganizationId = dailyUpdateMapperHelper.resolveTicketOrganizationId(
                dailyUpdate.getOrganizationId(), user.getId());
        Map<UUID, Ticket> addedTicketsById = dailyUpdateMapperHelper.resolveAccessibleTickets(user.getId(),
                ticketOrganizationId, dailyUpdateMapperHelper.findAddedTicketIds(dailyUpdate, lines));
        Set<UUID> syncTicketIds = findEditProgressSyncTicketIds(user, ticketOrganizationId, dailyUpdate, lines,
                addedTicketsById);

        List<DailyUpdateTicket> items = dailyUpdateMapperHelper.buildEditPreviewItems(
                dailyUpdate, lines, addedTicketsById);
        markProgressSynced(items, syncTicketIds);
        return dailyUpdateMapperHelper.toPreviewResponse(items, request.focus(), request.questions(),
                ticketOrganizationId, user.getId());
    }

    @Override
    public List<AllTicketListResponse> listSuggestedTickets(User user, UUID organizationId) {
        UUID resolved = organizationId != null
                ? organizationId
                : organizationMemberQueryService.findPrimaryActiveOrganizationId(user.getId()).orElse(null);
        if (resolved == null) {
            return List.of();
        }
        organizationAccessService.verifyOrganizationAccess(resolved, user.getId(), OrganizationMemberRole.GUEST,
                App.KAZAN);
        return ticketQueryService.listActiveAssignedTickets(user.getId(), resolved,
                DailyUpdateConstant.MAX_SUGGESTED_TICKETS);
    }

    @Override
    public UUID resolvePostingOrganizationId(User user, UUID organizationId) {
        UUID resolved = organizationId != null
                ? organizationId
                : organizationMemberQueryService.findPrimaryActiveOrganizationId(user.getId()).orElse(null);
        if (resolved != null) {
            organizationAccessService.verifyOrganizationAccess(resolved, user.getId(), OrganizationMemberRole.MEMBER,
                    App.KAZAN);
        }
        return resolved;
    }

    @Override
    public Page<DailyUpdateResponse> listDailyUpdates(User user, DailyUpdateQueryRequest request) {
        dailyUpdateValidator.ensureDateRangeValid(request.getFrom(), request.getTo());

        // Always newest first: the feed reads as a timeline, so the request's
        // sort fields are ignored, and an edit does not move an update.
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(),
                Sort.by(Sort.Direction.DESC, DailyUpdateConstant.FIELD_CREATED_AT));
        Page<DailyUpdate> page = dailyUpdateRepository.findAll(
                DailyUpdateSpecification.filterBy(
                        organizationMemberQueryService.getColleagueUserIds(user.getId()), request,
                        businessCalendar.dayRange(request.getFrom(), request.getTo())),
                pageable);
        return dailyUpdateMapperHelper.toResponsePage(page, user.getId());
    }

    @Override
    public DailyUpdateResponse getDailyUpdate(User user, UUID updateId) {
        DailyUpdate dailyUpdate = dailyUpdateValidator.ensureDailyUpdateVisible(
                organizationMemberQueryService.getColleagueUserIds(user.getId()), updateId);
        return dailyUpdateMapperHelper.toResponse(dailyUpdate, user.getId());
    }

    @Override
    public Map<UUID, DailyUpdateResponse> getDailyUpdatesByIds(User user, Collection<UUID> updateIds) {
        if (updateIds == null || updateIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> requestedIds = new LinkedHashSet<>(updateIds);
        List<DailyUpdate> dailyUpdates = dailyUpdateRepository.findAll(DailyUpdateSpecification.visibleByIds(
                organizationMemberQueryService.getColleagueUserIds(user.getId()), requestedIds));

        Map<UUID, DailyUpdateResponse> responsesById = new HashMap<>();
        for (DailyUpdateResponse response : dailyUpdateMapperHelper.toResponses(dailyUpdates, user.getId())) {
            responsesById.put(response.id(), response);
        }
        Map<UUID, DailyUpdateResponse> ordered = new LinkedHashMap<>();
        for (UUID updateId : requestedIds) {
            DailyUpdateResponse response = responsesById.get(updateId);
            if (response != null) {
                ordered.put(updateId, response);
            }
        }
        return ordered;
    }

    @Override
    public Optional<DailyUpdate> findDailyUpdate(UUID updateId) {
        return dailyUpdateRepository.findById(updateId);
    }

    @Override
    public DailyUpdateMissingResponse getMissingDailyUpdates(User user, DailyUpdateMissingRequest request) {
        UUID organizationId = request.getOrganizationId();
        organizationAccessService.verifyOrganizationAccess(organizationId, user.getId(), OrganizationMemberRole.ADMIN,
                App.KAZAN);

        ReportDay day = dailyUpdateDeadlineResolver.resolve(request.getDate());
        if (!day.workday()) {
            return dailyUpdateMapper.toMissingResponse(organizationId, day, 0, List.of());
        }

        // Memberships created at or after the deadline are left out, so a
        // member who joins during the day is not counted as late that day.
        // Agents post no daily updates, so an agent is never expected.
        // A member who cannot open Kazan in the organization is not expected
        // either: the nudge refuses that member, so the report must not list
        // a member the admin cannot reach. The kind and role filters run
        // first, so the access read is made only for people of an expected role.
        List<OrganizationMemberSummaryProjection> expectedMembers = new ArrayList<>();
        List<UUID> expectedUserIds = new ArrayList<>();
        for (OrganizationMemberKindSummaryProjection member
                : organizationMemberQueryService.listActiveMemberKindSummaries(organizationId, day.deadline())) {
            if (UserKind.AGENT.name().equals(member.getKind())) {
                continue;
            }
            if (!DailyUpdateConstant.EXPECTED_POSTER_ROLES.contains(OrganizationMemberRole.valueOf(member.getRole()))) {
                continue;
            }
            if (!appAccessQueryService.hasAppAccess(organizationId, member.getUserId(), App.KAZAN)) {
                continue;
            }
            expectedMembers.add(member);
            expectedUserIds.add(member.getUserId());
        }
        if (expectedMembers.isEmpty()) {
            return dailyUpdateMapper.toMissingResponse(organizationId, day, 0, List.of());
        }

        Map<UUID, Instant> firstPostedAtByAuthor = new HashMap<>();
        List<DailyUpdateFirstPostProjection> firstPosts = dailyUpdateRepository.findFirstPostsByAuthors(
                expectedUserIds, day.dayStart(), day.dayEnd());
        for (DailyUpdateFirstPostProjection firstPost : firstPosts) {
            firstPostedAtByAuthor.put(firstPost.getAuthorId(), firstPost.getFirstPostedAt());
        }

        List<DailyUpdateMissingMemberResponse> members = new ArrayList<>();
        for (OrganizationMemberSummaryProjection member : expectedMembers) {
            Instant firstPostedAt = firstPostedAtByAuthor.get(member.getUserId());
            if (isMissingOrLate(firstPostedAt, day.deadline())) {
                members.add(dailyUpdateMapper.toMissingMemberResponse(member, firstPostedAt));
            }
        }
        members.sort(MISSING_MEMBER_ORDER);
        return dailyUpdateMapper.toMissingResponse(organizationId, day, expectedMembers.size(), members);
    }

    @Override
    public Set<UUID> findProgressSyncTicketIds(User user, UUID organizationId, List<DailyUpdateTicketRequest> lines,
                                               Map<UUID, Ticket> ticketsById) {
        if (organizationId == null || ticketsById.isEmpty()) {
            return Set.of();
        }
        Set<UUID> contributorBoardIds = new HashSet<>(
                boardMemberQueryService.getContributorBoardIds(user.getId(), organizationId));
        Set<UUID> syncTicketIds = new LinkedHashSet<>();
        for (DailyUpdateTicketRequest line : lines) {
            Ticket ticket = ticketsById.get(line.ticketId());
            if (ticket != null
                    && ticket.getPercentage() != line.percentage()
                    && contributorBoardIds.contains(ticket.getBoard().getId())) {
                syncTicketIds.add(ticket.getId());
            }
        }
        return syncTicketIds;
    }

    @Override
    public Set<UUID> findEditProgressSyncTicketIds(User user, UUID organizationId, DailyUpdate dailyUpdate,
                                                   List<DailyUpdateTicketRequest> lines,
                                                   Map<UUID, Ticket> addedTicketsById) {
        // Only added lines and kept lines whose percentage changes are
        // candidates, so an edit never overwrites progress set by someone else
        // since. A kept line's ticket the user can no longer reach is absent
        // from the lookup and never syncs; the line itself survives the edit.
        Set<UUID> changedTicketIds = dailyUpdateMapperHelper.findChangedPercentageTicketIds(dailyUpdate, lines);
        Map<UUID, Ticket> ticketsById = new HashMap<>(addedTicketsById);
        if (organizationId != null && !changedTicketIds.isEmpty()) {
            for (Ticket ticket : ticketQueryService.getAccessibleTickets(user.getId(), organizationId,
                    new ArrayList<>(changedTicketIds))) {
                ticketsById.put(ticket.getId(), ticket);
            }
        }
        return findProgressSyncTicketIds(user, organizationId, lines, ticketsById);
    }

    /**
     * True when a member has no update before the deadline: none that day, or
     * a first update at or after the deadline.
     */
    private static boolean isMissingOrLate(Instant firstPostedAt, Instant deadline) {
        return firstPostedAt == null || !firstPostedAt.isBefore(deadline);
    }

    /**
     * Marks the detached preview lines that this request would sync.
     */
    private static void markProgressSynced(List<DailyUpdateTicket> items, Set<UUID> syncTicketIds) {
        for (DailyUpdateTicket item : items) {
            item.setProgressSynced(item.getTicketId() != null && syncTicketIds.contains(item.getTicketId()));
        }
    }
}
