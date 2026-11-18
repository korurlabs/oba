package com.korur.oba.apps.kazan.dailyupdate.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.service.ChatServiceLogConstant;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatMessageService;
import com.korur.oba.apps.kazan.dailyupdate.constant.service.DailyUpdateServiceLogConstant;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateCreateRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateTicketRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateUpdateRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdateTicket;
import com.korur.oba.apps.kazan.dailyupdate.mapper.DailyUpdateMapper;
import com.korur.oba.apps.kazan.dailyupdate.mapper.DailyUpdateMapperHelper;
import com.korur.oba.apps.kazan.dailyupdate.repository.DailyUpdateRepository;
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateService;
import com.korur.oba.apps.kazan.dailyupdate.validator.DailyUpdateValidator;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.service.TicketService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Implementation of daily update write operations.
 *
 * Each write runs in one transaction with its progress write-backs, so a
 * rejected write-back rolls the whole update back. Edit and delete first load
 * the update as the caller may read it (404 otherwise) and then check
 * authorship (403), so an update the caller cannot see is never confirmed to
 * exist.
 *
 * The update's chat channel entry is posted, marked edited and removed in the
 * same transaction as the update, so the channel never shows an update that
 * was rolled back and never loses one that was saved. The entry is posted
 * through the chat's ordinary send, with its checks: a refused send, such as
 * one over the send rate, rolls the new update back.
 */
@Service
@RequiredArgsConstructor
public class DailyUpdateServiceImpl implements DailyUpdateService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DailyUpdateServiceImpl.class);

    private final DailyUpdateRepository dailyUpdateRepository;
    private final DailyUpdateQueryService dailyUpdateQueryService;
    private final DailyUpdateValidator dailyUpdateValidator;
    private final DailyUpdateMapper dailyUpdateMapper;
    private final DailyUpdateMapperHelper dailyUpdateMapperHelper;
    private final TicketService ticketService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final ChatMessageService chatMessageService;
    private final ChatChannelQueryService chatChannelQueryService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public DailyUpdateResponse createDailyUpdate(User user, UUID organizationId, DailyUpdateCreateRequest request) {
        List<DailyUpdateTicketRequest> lines = request.tickets();
        dailyUpdateValidator.ensureWithinLimits(lines, request.focus(), request.questions());
        dailyUpdateValidator.ensureNoDuplicateTickets(lines);

        // Resolved first: the update's tickets must be on boards of the organization it is posted in.
        UUID postingOrganizationId = dailyUpdateQueryService.resolvePostingOrganizationId(user, organizationId);
        Map<UUID, Ticket> ticketsById = dailyUpdateMapperHelper.resolveAccessibleTickets(user.getId(),
                postingOrganizationId, lines.stream().map(DailyUpdateTicketRequest::ticketId).toList());
        Set<UUID> syncTicketIds = dailyUpdateQueryService.findProgressSyncTicketIds(user, postingOrganizationId,
                lines, ticketsById);

        DailyUpdate dailyUpdate = dailyUpdateMapper.toEntity(request);
        dailyUpdateMapperHelper.setCreateRelations(request, dailyUpdate, user, ticketsById);
        dailyUpdate.setOrganizationId(postingOrganizationId);
        dailyUpdate = dailyUpdateRepository.save(dailyUpdate);

        int syncedCount = syncProgress(user, postingOrganizationId, dailyUpdate, syncTicketIds);
        if (dailyUpdate.getOrganizationId() != null) {
            postChatEntry(user, dailyUpdate);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, DailyUpdateServiceLogConstant.DAILY_UPDATE_CREATED,
                dailyUpdate.getId(), dailyUpdate.getItems().size(), syncedCount);

        return dailyUpdateMapperHelper.toResponse(dailyUpdate, user.getId());
    }

    @Override
    @Transactional
    public DailyUpdateResponse editDailyUpdate(User user, UUID updateId, DailyUpdateUpdateRequest request) {
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
        // Computed before the lines are applied: it compares against the stored percentages.
        Set<UUID> syncTicketIds = dailyUpdateQueryService.findEditProgressSyncTicketIds(
                user, ticketOrganizationId, dailyUpdate, lines, addedTicketsById);

        dailyUpdateMapper.updateEntity(dailyUpdate, request);
        dailyUpdateMapperHelper.setUpdateRelations(request, dailyUpdate, addedTicketsById);
        dailyUpdate.setEditedAt(Instant.now());
        dailyUpdate = dailyUpdateRepository.save(dailyUpdate);

        int syncedCount = syncProgress(user, ticketOrganizationId, dailyUpdate, syncTicketIds);
        chatMessageService.touchDailyUpdateMessage(dailyUpdate);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, DailyUpdateServiceLogConstant.DAILY_UPDATE_EDITED,
                updateId, dailyUpdate.getItems().size(), syncedCount);

        return dailyUpdateMapperHelper.toResponse(dailyUpdate, user.getId());
    }

    @Override
    @Transactional
    public void deleteDailyUpdate(User user, UUID updateId) {
        DailyUpdate dailyUpdate = dailyUpdateValidator.ensureDailyUpdateVisible(
                organizationMemberQueryService.getColleagueUserIds(user.getId()), updateId);
        dailyUpdateValidator.ensureAuthor(dailyUpdate, user);

        // Marks only the update as deleted. A JPA delete would cascade to the
        // lines, which are not soft-deletable, and remove what the author
        // reported along with the update.
        dailyUpdateRepository.deleteByIdWithoutRelations(dailyUpdate.getId());
        chatMessageService.removeDailyUpdateMessage(dailyUpdate);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, DailyUpdateServiceLogConstant.DAILY_UPDATE_DELETED,
                updateId);
    }

    /**
     * Posts the entry of a new update to its organization's daily updates
     * channel, as the author. An organization without that channel shows no
     * card, and the update is saved all the same.
     */
    private void postChatEntry(User user, DailyUpdate dailyUpdate) {
        UUID organizationId = dailyUpdate.getOrganizationId();
        Optional<UUID> channelId = chatChannelQueryService.findDailyUpdateChannelId(organizationId);
        if (channelId.isEmpty()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ChatServiceLogConstant.CHAT_DAILY_UPDATE_CHANNEL_ABSENT,
                    organizationId, dailyUpdate.getId());
            return;
        }
        chatMessageService.postMessage(user, organizationId,
                dailyUpdateMapper.toChatMessageCreateRequest(dailyUpdate, channelId.get()), List.of());
    }

    /**
     * Writes each eligible line's percentage to its ticket and marks the line
     * synced. A line stays marked after later edits that do not sync it. The
     * tickets were resolved in organizationId, so the write is checked there.
     *
     * @return the number of tickets synced
     */
    private int syncProgress(User user, UUID organizationId, DailyUpdate dailyUpdate, Set<UUID> syncTicketIds) {
        int syncedCount = 0;
        for (DailyUpdateTicket item : dailyUpdate.getItems()) {
            if (item.getTicketId() != null && syncTicketIds.contains(item.getTicketId())) {
                ticketService.syncTicketPercentage(user, organizationId, item.getTicketId(), item.getPercentage());
                item.setProgressSynced(true);
                syncedCount++;
                loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                        DailyUpdateServiceLogConstant.DAILY_UPDATE_TICKET_PROGRESS_SYNCED,
                        dailyUpdate.getId(), item.getTicketId(), item.getPercentage());
            }
        }
        return syncedCount;
    }
}
