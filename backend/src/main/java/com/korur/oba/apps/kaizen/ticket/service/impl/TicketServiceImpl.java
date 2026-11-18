package com.korur.oba.apps.kaizen.ticket.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.repository.BoardRepository;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.board.service.BoardQueryService;
import com.korur.oba.apps.kaizen.board.validator.BoardValidator;
import com.korur.oba.apps.kaizen.comment.repository.CommentRepository;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.label.entity.Label;
import com.korur.oba.apps.kaizen.label.validator.LabelValidator;
import com.korur.oba.apps.kaizen.milestone.entity.Milestone;
import com.korur.oba.apps.kaizen.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kaizen.project.entity.Project;
import com.korur.oba.apps.kaizen.project.validator.ProjectValidator;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.service.SprintService;
import com.korur.oba.apps.kaizen.sprint.validator.SprintValidator;
import com.korur.oba.apps.kaizen.systemactor.entity.SystemActor;
import com.korur.oba.apps.kaizen.ticket.constant.TicketConstant;
import com.korur.oba.apps.kaizen.ticket.constant.TicketExceptionLogConstant;
import com.korur.oba.apps.kaizen.ticket.constant.service.TicketServiceLogConstant;
import com.korur.oba.apps.kaizen.ticket.dto.command.IncidentReport;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketBulkMoveRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketCreateRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketMoveRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketOrderUpdateRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketUpdateRequest;
import com.korur.oba.apps.kaizen.ticket.dto.response.TicketBulkMoveResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.TicketDetailResponse;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kaizen.ticket.entity.TicketLabel;
import com.korur.oba.apps.kaizen.ticket.entity.TicketReviewer;
import com.korur.oba.apps.kaizen.ticket.event.IncidentOpenedEvent;
import com.korur.oba.apps.kaizen.ticket.enums.IncidentOutcome;
import com.korur.oba.apps.kaizen.ticket.enums.MissionRank;
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.apps.kaizen.ticket.helper.TicketHelper;
import com.korur.oba.apps.kaizen.ticket.helper.TicketStatusNormalizer;
import com.korur.oba.apps.kaizen.ticket.helper.TicketTypeNormalizer;
import com.korur.oba.apps.kaizen.ticket.mapper.TicketMapper;
import com.korur.oba.apps.kaizen.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketLabelRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketReviewerRepository;
import com.korur.oba.apps.kaizen.ticket.service.TicketService;
import com.korur.oba.apps.kaizen.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Implementation of ticket write operations.
 * <p>
 * Manages ticket lifecycle including creation, updates, movement between
 * sprints and backlog, sub-ticket creation, and assignee/reviewer management.
 */
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(TicketServiceImpl.class);

    private final TicketRepository ticketRepository;
    private final TicketAssigneeRepository ticketAssigneeRepository;
    private final TicketReviewerRepository ticketReviewerRepository;
    private final TicketLabelRepository ticketLabelRepository;
    private final CommentRepository commentRepository;

    private final BoardRepository boardRepository;
    private final BoardAccessService boardAccessService;
    private final BoardMemberQueryService boardMemberQueryService;
    private final BoardQueryService boardQueryService;
    private final BoardValidator boardValidator;
    private final AppAccessValidator appAccessValidator;
    private final SprintService sprintService;
    private final SprintValidator sprintValidator;
    private final BacklogValidator backlogValidator;
    private final ProjectValidator projectValidator;
    private final TicketValidator ticketValidator;
    private final LabelValidator labelValidator;
    private final MilestoneValidator milestoneValidator;

    private final TicketMapper ticketMapper;
    private final TicketHelper ticketHelper;
    private final TicketStatusNormalizer ticketStatusNormalizer;
    private final TicketTypeNormalizer ticketTypeNormalizer;

    private final UserQueryService userQueryService;
    private final LoggingService loggingService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public TicketDetailResponse createTicket(User user, UUID organizationId, UUID boardId, TicketCreateRequest request) {
        // Validate board exists and user has contribute access.
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Auto-generate the ticket key from board prefix + atomic counter.
        long nextNumber = boardRepository.incrementAndGetTicketCounter(boardId);
        String ticketKey = board.getPrefix() + "-" + nextNumber;

        // Map request to entity and set computed/relational fields.
        Ticket ticket = ticketMapper.toEntity(request);
        ticket.setKey(ticketKey);
        ticket.setCreatedBy(user);
        ticket.setBoard(board);
        ticket.setDisplayOrder(ticketRepository.maxDisplayOrderByBoard_Id(boardId) + 1);

        UUID sprintId = request.sprintId();
        UUID backlogId = request.backlogId();

        // Resolve optional parent ticket for sub-ticket relationships. A sub-ticket is placed
        // where its parent is, so the parent's placement replaces both requested ones.
        if (request.parentTicketId() != null) {
            Ticket parentTicket = ticketValidator.ensureTicketExists(boardId, request.parentTicketId());
            ticket.setParentTicket(parentTicket);

            if (parentTicket.getSprint() != null) {
                sprintId = parentTicket.getSprint().getId();
                backlogId = null;
            } else if (parentTicket.getBacklog() != null) {
                backlogId = parentTicket.getBacklog().getId();
                sprintId = null;
            }
        }

        // Validate at least one placement is provided.
        ticketValidator.ensureTicketHasCorrectPlacement(sprintId, backlogId);

        // Resolve optional sprint assignment. The sprint must belong to the board's organization.
        if (sprintId != null) {
            Sprint sprint = sprintValidator.ensureSprintExistsInOrganization(organizationId, sprintId);
            ticket.setSprint(sprint);
            sprintService.linkSprintToBoardIfNeeded(sprint, board);
        }

        // Resolve optional backlog assignment.
        if (backlogId != null) {
            Backlog backlog = backlogValidator.ensureBacklogExistsByBoard(backlogId, boardId);
            ticket.setBacklog(backlog);
        }

        // Resolve optional project assignment from a board of the same organization.
        if (request.projectId() != null) {
            Project project = projectValidator.ensureProjectInOrganization(organizationId, request.projectId());
            ticket.setProject(project);
        }

        // Resolve optional milestone assignment.
        if (request.milestoneId() != null) {
            ticket.setMilestone(resolveLinkableMilestone(user, organizationId, request.milestoneId(), board));
        }

        // Attach labels from the provided label IDs via TicketLabel join entities. Each label
        // must be on a board of the same organization.
        if (request.labelIds() != null && !request.labelIds().isEmpty()) {
            for (UUID labelId : request.labelIds()) {
                Label label = labelValidator.ensureLabelInOrganization(organizationId, labelId);
                TicketLabel ticketLabel = TicketLabel.builder()
                        .ticket(ticket)
                        .label(label)
                        .build();
                ticket.getTicketLabels().add(ticketLabel);
            }
        }

        // A top-level backlog ticket may only be TODO. The status is required on create, so a
        // different one is refused rather than reset without a word.
        ticketValidator.ensureBacklogTicketStatusAllowed(ticket);
        ticketStatusNormalizer.normalizeBacklogStatus(ticket);

        // An incident created as DONE must already document its cause.
        if (ticket.getType() == TicketType.INCIDENT) {
            ticketValidator.ensureIncidentCanBeClosed(ticket, null);
        }

        // TASK is a deprecated alias that is stored as FEATURE.
        ticketTypeNormalizer.normalizeType(ticket);

        // Create assignees from the provided user IDs, each an active member of the
        // board's organization who may open Kaizen there.
        if (request.assigneeIds() != null && !request.assigneeIds().isEmpty()) {
            for (UUID assigneeId : request.assigneeIds()) {
                boardValidator.ensureUserIsOrganizationMember(organizationId, assigneeId);
            }
            appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.KAIZEN, request.assigneeIds());
            for (UUID assigneeId : request.assigneeIds()) {
                User assigneeUser = userQueryService.getUserEntityById(assigneeId);
                TicketAssignee assignee = TicketAssignee.builder()
                        .ticket(ticket)
                        .user(assigneeUser)
                        .build();
                ticket.getAssignees().add(assignee);
            }
        }

        // Create reviewers from the provided user IDs, each an active member of the
        // board's organization who may open Kaizen there.
        if (request.reviewerIds() != null && !request.reviewerIds().isEmpty()) {
            for (UUID reviewerId : request.reviewerIds()) {
                boardValidator.ensureUserIsOrganizationMember(organizationId, reviewerId);
            }
            appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.KAIZEN, request.reviewerIds());
            for (UUID reviewerId : request.reviewerIds()) {
                User reviewerUser = userQueryService.getUserEntityById(reviewerId);
                TicketReviewer reviewer = TicketReviewer.builder()
                        .ticket(ticket)
                        .user(reviewerUser)
                        .build();
                ticket.getReviewers().add(reviewer);
            }
        }

        // The persist cascades to the labels, assignees and reviewers added above, so
        // each gets its id now. A child added after it would only be persisted at the
        // flush after the response is mapped, and the response would carry a null id.
        ticket = ticketRepository.save(ticket);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.TICKET_CREATED, ticket.getId());

        if (isUrgentOpenIncident(ticket.getType(), ticket.getStatus(), ticket.getPriority())) {
            publishIncidentOpened(ticket, organizationId, user.getId());
        }

        return ticketMapper.toDetailResponse(ticket);
    }

    @Override
    @Transactional
    public TicketDetailResponse updateTicket(User user, UUID organizationId, UUID boardId, UUID ticketId,
            TicketUpdateRequest request) {
        // Validate board exists and user has contribute access.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Validate ticket exists and apply partial update.
        Ticket ticket = ticketValidator.ensureTicketExists(boardId, ticketId);

        // The urgency before the update decides whether the update opens an urgent incident
        // that the board members have not been alerted to yet.
        boolean wasUrgentOpenIncident = isUrgentOpenIncident(
                ticket.getType(), ticket.getStatus(), ticket.getPriority());
        TicketPriority previousPriority = ticket.getPriority();
        TicketStatus previousStatus = ticket.getStatus();

        ticketMapper.updateEntity(ticket, request);

        // TASK is a deprecated alias that is stored as FEATURE. It is replaced before anything
        // flushes the ticket, because the database type enum has no TASK value.
        ticketTypeNormalizer.normalizeType(ticket);

        // Users already on the ticket are read before the lists are replaced. An update sends
        // the full lists, so a user who has since left the organization or lost Kaizen can stay
        // on the ticket; only a user the update adds must be an active member of the
        // organization who may open Kaizen there.
        Set<UUID> currentAssigneeIds = ticket.getAssignees().stream()
                .map((TicketAssignee assignee) -> assignee.getUser().getId())
                .collect(Collectors.toSet());
        Set<UUID> currentReviewerIds = ticket.getReviewers().stream()
                .map((TicketReviewer reviewer) -> reviewer.getUser().getId())
                .collect(Collectors.toSet());

        if (request.assigneeIds() != null) {
            ticket.getAssignees().clear();
        }

        if (request.reviewerIds() != null) {
            ticket.getReviewers().clear();
        }

        if (request.labelIds() != null) {
            ticket.getTicketLabels().clear();
        }

        // Flush orphan deletions before inserting new entries to avoid unique
        // constraint violations.
        if (request.assigneeIds() != null || request.reviewerIds() != null || request.labelIds() != null) {
            ticket = ticketRepository.saveAndFlush(ticket);
        }

        if (request.assigneeIds() != null) {
            List<UUID> addedAssigneeIds = new ArrayList<>();
            for (UUID assigneeId : request.assigneeIds()) {
                if (!currentAssigneeIds.contains(assigneeId)) {
                    boardValidator.ensureUserIsOrganizationMember(organizationId, assigneeId);
                    addedAssigneeIds.add(assigneeId);
                }
            }
            appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.KAIZEN, addedAssigneeIds);
            for (UUID assigneeId : request.assigneeIds()) {
                User assigneeUser = userQueryService.getUserEntityById(assigneeId);
                TicketAssignee assignee = TicketAssignee.builder()
                        .ticket(ticket)
                        .user(assigneeUser)
                        .build();
                ticket.getAssignees().add(assignee);
            }
        }

        if (request.reviewerIds() != null) {
            List<UUID> addedReviewerIds = new ArrayList<>();
            for (UUID reviewerId : request.reviewerIds()) {
                if (!currentReviewerIds.contains(reviewerId)) {
                    boardValidator.ensureUserIsOrganizationMember(organizationId, reviewerId);
                    addedReviewerIds.add(reviewerId);
                }
            }
            appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.KAIZEN, addedReviewerIds);
            for (UUID reviewerId : request.reviewerIds()) {
                User reviewerUser = userQueryService.getUserEntityById(reviewerId);
                TicketReviewer reviewer = TicketReviewer.builder()
                        .ticket(ticket)
                        .user(reviewerUser)
                        .build();
                ticket.getReviewers().add(reviewer);
            }
        }

        if (request.labelIds() != null) {
            for (UUID labelId : request.labelIds()) {
                Label label = labelValidator.ensureLabelInOrganization(organizationId, labelId);
                TicketLabel ticketLabel = TicketLabel.builder()
                        .ticket(ticket)
                        .label(label)
                        .build();
                ticket.getTicketLabels().add(ticketLabel);
            }
        }

        if (request.projectId() != null) {
            Project project = projectValidator.ensureProjectInOrganization(organizationId, request.projectId());
            ticket.setProject(project);
        }

        // clearMilestone wins over milestoneId so a client can never attach and clear at once.
        if (Boolean.TRUE.equals(request.clearMilestone())) {
            ticket.setMilestone(null);
        } else if (request.milestoneId() != null) {
            ticket.setMilestone(resolveLinkableMilestone(user, organizationId, request.milestoneId(), ticket.getBoard()));
        }

        // clearParent wins over parentTicketId so a client can never reparent and unparent at once.
        // A sub-ticket is planned on its own, so neither changes the placement of the ticket or of
        // its sub-tickets. A null parentTicketId alone keeps the parent because partial updates
        // omit it.
        if (Boolean.TRUE.equals(request.clearParent())) {
            ticketHelper.unparent(ticket);
        } else if (request.parentTicketId() != null) {
            Ticket parentTicket = ticketValidator.ensureTicketExists(boardId, request.parentTicketId());
            ticketHelper.assignParent(ticket, parentTicket);
        }

        // Top-level backlog tickets may only be TODO. A status the request sets is refused when the
        // ticket ends up a top-level backlog ticket; without one, a backlog sub-ticket that is
        // unparented is reset.
        if (request.status() != null) {
            ticketValidator.ensureBacklogTicketStatusAllowed(ticket);
        }
        ticketStatusNormalizer.normalizeBacklogStatus(ticket);

        // Closing an incident needs its RCA and lessons learned. The type is read after the type
        // normalizer, so a ticket that becomes an incident in this request counts.
        if (ticket.getType() == TicketType.INCIDENT) {
            ticketValidator.ensureIncidentCanBeClosed(ticket, previousStatus);
        }

        ticket = ticketRepository.save(ticket);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.TICKET_UPDATED, ticket.getId());

        // The board members are alerted when the update makes the ticket an urgent open incident
        // (by its type, a raised priority or a reopen) or raises an urgent one from P2 to P1. Any
        // other edit of an incident they were already alerted to stays quiet.
        if (isUrgentOpenIncident(ticket.getType(), ticket.getStatus(), ticket.getPriority())
                && (!wasUrgentOpenIncident || ticket.getPriority().compareTo(previousPriority) > 0)) {
            publishIncidentOpened(ticket, organizationId, user.getId());
        }

        return ticketMapper.toDetailResponse(ticket);
    }

    @Override
    @Transactional
    public void deleteTicket(User user, UUID organizationId, UUID boardId, UUID ticketId) {
        // Validate board exists and user has contribute access.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Validate ticket exists and delete.
        Ticket ticket = ticketValidator.ensureTicketExists(boardId, ticketId);
        ticketRepository.delete(ticket);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.TICKET_DELETED, ticketId);
    }

    @Override
    @Transactional
    public TicketDetailResponse moveTicket(User user, UUID organizationId, UUID boardId, UUID ticketId,
            TicketMoveRequest request) {
        // Validate board exists and user has contribute access.
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Validate ticket exists.
        Ticket ticket = ticketValidator.ensureTicketExists(boardId, ticketId);

        ticketValidator.ensureTicketHasCorrectPlacement(request.sprintId(), request.backlogId());

        // The move takes along the sub-tickets the mode picks, keeps every parent, and applies
        // the backlog status rule. It runs first because it validates the sub-ticket selection
        // before it writes, so a refused move leaves the sprint link and the order untouched.
        if (request.sprintId() != null) {
            Sprint sprint = sprintValidator.ensureSprintExistsInOrganization(organizationId, request.sprintId());
            ticketHelper.moveTickets(
                    List.of(ticket), request.subTicketMode(), request.subTicketIds(),
                    TicketRelocationAction.MOVE_TO_SPRINT, null, sprint, null, boardId);
            sprintService.linkSprintToBoardIfNeeded(sprint, board);
        } else {
            Backlog backlog = backlogValidator.ensureBacklogExistsByBoard(request.backlogId(), boardId);
            ticketHelper.moveTickets(
                    List.of(ticket), request.subTicketMode(), request.subTicketIds(),
                    TicketRelocationAction.MOVE_TO_BACKLOG, null, null, backlog, boardId);
        }

        // A drop between two rows sends the order of that slot. The board-wide shift runs after
        // the relocation, which is flushed first so that its full-row updates cannot later
        // overwrite the shifted orders. The bulk update bypasses the loaded tickets, so the
        // persistence context is then cleared and the ticket read again: the response shows the
        // stored orders of its sub-tickets, and no stale loaded ticket can be written back. An
        // order equal to the current one is skipped rather than rejected, because a client with
        // a stale list may send it.
        if (request.order() != null && request.order() != ticket.getDisplayOrder()) {
            entityManager.flush();
            applyDisplayOrder(boardId, ticket, request.order());
            entityManager.flush();
            entityManager.clear();
            ticket = ticketValidator.ensureTicketExists(boardId, ticketId);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.TICKET_MOVED, ticketId, ticket.getId());

        return ticketMapper.toDetailResponse(ticket);
    }

    @Override
    @Transactional
    public TicketDetailResponse updateOrder(User user, UUID organizationId, UUID boardId, UUID ticketId,
            TicketOrderUpdateRequest request) {
        // Validate board exists and user has contribute access.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Validate ticket exists and update display order with sibling shifting.
        Ticket ticket = ticketValidator.ensureTicketExists(boardId, ticketId);
        int oldOrder = ticket.getDisplayOrder();
        int newOrder = request.order();

        // Ensure the ticket order is correct.
        ticketValidator.ensureTicketOrderIsCorrect(oldOrder, newOrder);

        applyDisplayOrder(boardId, ticket, newOrder);
        ticket = ticketRepository.save(ticket);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.TICKET_UPDATED, ticket.getId());

        return ticketMapper.toDetailResponse(ticket);
    }

    @Override
    @Transactional
    public TicketDetailResponse createTicketDirect(
            User user,
            Board board,
            Backlog backlog,
            String title,
            String description,
            String key,
            TicketType type,
            TicketPriority priority) {

        Ticket ticket = saveTodoBacklogTicket(board, backlog, title, description, key, type, priority,
                created -> created.setCreatedBy(user));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.TICKET_CREATED_FROM_SUGGESTION, ticket.getId());

        if (isUrgentOpenIncident(ticket.getType(), ticket.getStatus(), ticket.getPriority())) {
            publishIncidentOpened(ticket, board.getOrganizationId(), user.getId());
        }

        return ticketMapper.toDetailResponse(ticket);
    }

    @Override
    @Transactional
    public IncidentOutcome reportIncident(
            SystemActor actor,
            UUID organizationId,
            UUID boardId,
            IncidentReport report,
            boolean ticketCreationAllowed) {

        // No verifyBoardAccess: a system actor is no board member, and the security chain
        // has already granted it the right to report. The board and its backlog are checked
        // before the lock, so a misconfigured board fails without holding a row lock.
        // The reporter's organization is bound to its credential; a board of another
        // organization is a missing one, so an alert never lands outside that organization.
        boardValidator.ensureBoardInOrganization(organizationId, boardId);
        Backlog backlog = backlogValidator.ensureBacklogExistsByBoardId(boardId);

        // Serializes concurrent reports for this board, so the open-ticket lookup and the
        // insert below cannot interleave and create two tickets for one incident.
        Board board = boardQueryService.lockForUpdate(boardId);

        // One open ticket per incident: a repeated report must leave the board alone.
        Optional<Ticket> openTicket = ticketRepository
                .findFirstByBoard_IdAndIncidentKeyAndStatusNotOrderByCreatedAtDesc(
                        boardId, report.key(), TicketStatus.DONE);
        if (openTicket.isPresent()) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    TicketServiceLogConstant.INCIDENT_TICKET_ALREADY_OPEN,
                    report.key(), boardId, openTicket.get().getKey());
            return IncidentOutcome.IGNORED;
        }

        // The caller decides how many tickets one notification may open.
        if (!ticketCreationAllowed) {
            return IncidentOutcome.IGNORED;
        }

        // Auto-generate the ticket key from board prefix + atomic counter.
        long nextNumber = boardRepository.incrementAndGetTicketCounter(boardId);
        String ticketKey = board.getPrefix() + "-" + nextNumber;

        Ticket ticket = saveTodoBacklogTicket(board, backlog, report.title(), report.description(), ticketKey,
                TicketType.INCIDENT, TicketPriority.P1,
                created -> {
                    created.setCreatedBySystemActor(actor);
                    created.setIncidentKey(report.key());
                });

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.INCIDENT_TICKET_CREATED, ticket.getKey(), boardId, actor.getType());

        // A monitoring source is no user, so no board member is left out as the actor.
        publishIncidentOpened(ticket, organizationId, null);

        return IncidentOutcome.CREATED;
    }

    /**
     * Tells whether a ticket is an open incident urgent enough to alert its board members on
     * their phones: an {@link TicketType#INCIDENT} of priority {@link TicketPriority#P1} or
     * {@link TicketPriority#P2} that is not {@link TicketStatus#DONE}. A P3 incident waits for
     * the board like any other ticket.
     */
    private boolean isUrgentOpenIncident(TicketType type, TicketStatus status, TicketPriority priority) {
        return type == TicketType.INCIDENT
                && status != TicketStatus.DONE
                && (priority == TicketPriority.P1 || priority == TicketPriority.P2);
    }

    /**
     * Publishes the urgent incident for the alert that goes out after the commit. The board name
     * is read here, inside the transaction that holds the board.
     */
    private void publishIncidentOpened(Ticket ticket, UUID organizationId, UUID actorId) {
        Board board = ticket.getBoard();
        applicationEventPublisher.publishEvent(new IncidentOpenedEvent(
                organizationId,
                board.getId(),
                board.getName(),
                ticket.getId(),
                ticket.getKey(),
                ticket.getTitle(),
                ticket.getPriority(),
                actorId));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.INCIDENT_OPENED_PUBLISHED,
                ticket.getKey(), board.getId(), ticket.getPriority());
    }

    /**
     * Builds and saves a TODO ticket in a backlog with only the fields every direct creation
     * shares. The creator differs per caller (a user or a system actor, never both), so each
     * caller sets it, and any field of its own, through creatorSetter before the save.
     */
    private Ticket saveTodoBacklogTicket(
            Board board,
            Backlog backlog,
            String title,
            String description,
            String key,
            TicketType type,
            TicketPriority priority,
            Consumer<Ticket> creatorSetter) {

        // Build the ticket entity with minimal fields.
        Ticket ticket = Ticket.builder()
                .title(title)
                .description(description)
                .key(key)
                .type(type)
                .priority(priority)
                .status(TicketStatus.TODO)
                // These tickets have no estimate from a person (AI suggestions, monitoring
                // incidents), and M is the size most tickets have.
                .missionRank(MissionRank.M)
                .board(board)
                .backlog(backlog)
                .ticketLabels(new ArrayList<>())
                .build();
        creatorSetter.accept(ticket);

        // Backlog tickets may only be TODO.
        ticketStatusNormalizer.normalizeBacklogStatus(ticket);

        // TASK is a deprecated alias that is stored as FEATURE.
        ticketTypeNormalizer.normalizeType(ticket);

        return ticketRepository.save(ticket);
    }

    @Override
    @Transactional
    public TicketBulkMoveResponse bulkMoveTickets(User user, UUID organizationId, UUID boardId, TicketBulkMoveRequest request) {
        // Validate board exists.
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // DELETE action is not allowed for bulk move.
        ticketValidator.ensureBulkMoveActionValid(request.action());

        // Load tickets by IDs — tickets may originate from different related boards
        // (visible via the includeAllBoards filter on the frontend).
        List<Ticket> tickets = ticketRepository.findAllById(request.ticketIds());
        ticketValidator.ensureAllTicketsFound(tickets, request.ticketIds());

        // Validate the user has contribute access to every distinct source board.
        tickets.stream()
                .map(t -> t.getBoard().getId())
                .distinct()
                .forEach(sourceBoardId -> boardAccessService.verifyBoardAccess(
                        organizationId, sourceBoardId, user.getId(), BoardMemberRole.MEMBER));

        // Resolve the target board if cross-board relocation is requested.
        Board targetBoard = null;
        if (request.targetBoardId() != null) {
            targetBoard = boardAccessService.verifyBoardAccess(
                    organizationId, request.targetBoardId(), user.getId(), BoardMemberRole.MEMBER);
        }

        // Effective board is the destination for all tickets.
        Board effectiveBoard = targetBoard != null ? targetBoard : board;
        UUID effectiveBoardId = effectiveBoard.getId();

        // When tickets originate from multiple boards, ensure targetBoard is set
        // so that the move applies cross-board field updates for tickets
        // not already on the effective board.
        boolean hasCrossBoardTickets = tickets.stream()
                .anyMatch(t -> !t.getBoard().getId().equals(effectiveBoardId));
        if (hasCrossBoardTickets && targetBoard == null) {
            targetBoard = board;
        }

        // Resolve targets based on the action.
        Backlog targetBacklog = null;
        Sprint targetSprint = null;

        switch (request.action()) {
            case MOVE_TO_BACKLOG -> targetBacklog = backlogValidator.ensureBacklogExistsByBoardId(effectiveBoardId);
            case MOVE_TO_SPRINT -> {
                ticketValidator.ensureTargetSprintProvided(request.targetSprintId(), boardId);
                targetSprint = sprintValidator.ensureSprintExists(request.targetSprintId());
                sprintValidator.ensureSprintBelongsToBoard(targetSprint, effectiveBoardId);
            }
            default -> { /* covered by DELETE guard above */ }
        }

        // The direct sub-tickets the caller leaves behind become top-level tickets on their own
        // board before the move, which then no longer finds them in the subtree it carries.
        if (Boolean.TRUE.equals(request.detachSubTickets())) {
            ticketHelper.detachSubTicketsFromBoardChanges(tickets, targetBoard);
        }

        List<String> milestoneClearedTicketKeys = ticketHelper.moveTickets(
                tickets, request.subTicketMode(), request.subTicketIds(),
                request.action(), targetBoard, targetSprint, targetBacklog, boardId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.TICKETS_BULK_MOVED,
                tickets.size(), boardId);

        return new TicketBulkMoveResponse(tickets.size(), milestoneClearedTicketKeys);
    }

    @Override
    @Transactional
    public void syncTicketPercentage(User user, UUID organizationId, UUID ticketId, int percentage) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> {
                    // Log for observability before propagating the not-found error.
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.SERVICE,
                            TicketExceptionLogConstant.TICKET_NOT_FOUND,
                            ticketId);
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_TICKET_FOUND,
                            Map.of(TicketConstant.FIELD_TICKET_ID, ticketId));
                });

        // Same contribute rule as a ticket update, checked here so no caller can bypass it.
        boardAccessService.verifyBoardAccess(
                organizationId, ticket.getBoard().getId(), user.getId(), BoardMemberRole.MEMBER);

        ticket.setPercentage(percentage);
        ticketRepository.save(ticket);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.TICKET_PERCENTAGE_SYNCED, ticketId, percentage);
    }

    @Override
    @Transactional
    public void restoreTicket(User user, UUID organizationId, UUID boardId, UUID ticketId) {
        // Validate board exists and user has contribute access.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Find ticket including deleted records.
        Ticket ticket = ticketRepository.findByIdWithDeleted(ticketId)
                .orElseThrow(() -> new NotFoundException(
                        ExceptionDomainKaizenError.NOT_TICKET_FOUND,
                        Map.of(TicketConstant.FIELD_TICKET_ID, ticketId)));

        // Validate the ticket is actually deleted.
        ticketValidator.ensureTicketIsDeleted(ticket);

        // Validate ticket belongs to the specified board.
        if (!ticket.getBoard().getId().equals(boardId)) {
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_TICKET_BOARD_MISMATCH,
                    Map.of(TicketConstant.FIELD_TICKET_ID, ticketId,
                            TicketConstant.FIELD_BOARD_ID, boardId));
        }

        // Restore the ticket and all its cascade-deleted children recursively.
        restoreTicketWithChildren(ticketId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.TICKET_RESTORED, ticketId);
    }

    /**
     * Restores a ticket and all its cascade-deleted children:
     * comments, assignees, reviewers, ticket-labels, and sub-tickets recursively.
     */
    private void restoreTicketWithChildren(UUID ticketId) {
        // Restore the ticket itself.
        ticketRepository.restoreById(ticketId);

        // Restore all cascade-deleted children.
        int restoredComments = commentRepository.restoreAllByTicketId(ticketId);
        int restoredAssignees = ticketAssigneeRepository.restoreAllByTicketId(ticketId);
        int restoredReviewers = ticketReviewerRepository.restoreAllByTicketId(ticketId);
        int restoredLabels = ticketLabelRepository.restoreAllByTicketId(ticketId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                TicketServiceLogConstant.TICKET_CHILDREN_RESTORED,
                ticketId, restoredComments, restoredAssignees, restoredReviewers, restoredLabels);

        // Restore sub-tickets recursively.
        List<Ticket> deletedSubTickets = ticketRepository.findDeletedByParentTicketId(ticketId);
        if (!deletedSubTickets.isEmpty()) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    TicketServiceLogConstant.TICKET_SUB_TICKETS_RESTORED,
                    deletedSubTickets.size(), ticketId);

            for (Ticket subTicket : deletedSubTickets) {
                restoreTicketWithChildren(subTicket.getId());
            }
        }
    }

    /**
     * Loads a milestone for attachment to a ticket on ticketBoard. A milestone on
     * a board the user is not a member of is reported as not found, so its
     * existence does not leak; it must also live on a board related to ticketBoard.
     */
    private Milestone resolveLinkableMilestone(User user, UUID organizationId, UUID milestoneId, Board ticketBoard) {
        Milestone milestone = milestoneValidator.ensureMilestoneExistsOnBoards(
                milestoneId, boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId));
        milestoneValidator.ensureMilestoneLinkableToBoard(milestone, ticketBoard);
        return milestone;
    }

    /**
     * Gives the ticket newOrder and shifts the board's tickets between its old and new order by
     * one, so every other ticket keeps its relative position and no two tickets share a slot.
     * The shift is board-wide because sprint and backlog lists of a board share one order.
     *
     * The caller saves the ticket: {@link #updateOrder} saves it directly, {@link #moveTicket}
     * flushes it after the relocation and reads it again. The caller also ensures newOrder differs
     * from the current order.
     */
    private void applyDisplayOrder(UUID boardId, Ticket ticket, int newOrder) {
        int oldOrder = ticket.getDisplayOrder();
        if (newOrder < oldOrder) {
            ticketRepository.shiftDisplayOrderUp(boardId, newOrder, oldOrder, ticket.getId());
        } else {
            ticketRepository.shiftDisplayOrderDown(boardId, oldOrder, newOrder, ticket.getId());
        }
        ticket.setDisplayOrder(newOrder);
    }
}
