package com.korur.oba.apps.kaizen.ticket.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

// 3. Java SDK
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.board.entity.Board;
// 4. Internal
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.helper.BoardHelper;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.sprint.service.SprintQueryService;
import com.korur.oba.apps.kaizen.ticket.dto.request.AllTicketQueryRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketQueryRequest;
import com.korur.oba.apps.kaizen.ticket.dto.response.AllTicketListResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeSummaryResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.TicketDetailResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.TicketListResponse;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.apps.kaizen.ticket.mapper.TicketMapper;
import com.korur.oba.apps.kaizen.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kaizen.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.apps.kaizen.ticket.specification.TicketSpecification;
import com.korur.oba.apps.kaizen.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class TicketQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketValidator ticketValidator;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private BoardHelper boardHelper;

    @Mock
    private BoardMemberQueryService boardMemberQueryService;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private SprintQueryService sprintQueryService;

    @Mock
    private TicketAssigneeRepository ticketAssigneeRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private TicketQueryServiceImpl ticketQueryServiceImpl;

    @Nested
    class ListTickets {

        @Test
        void userIsNotBoardMember_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            TicketQueryRequest queryRequest = new TicketQueryRequest();

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER))
                    .thenThrow(new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    ticketQueryServiceImpl.listTickets(user, organizationId, boardId, queryRequest));
        }

        @Test
        void userIsBoardMember_returnsTicketList() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().id(boardId).build();

            Ticket ticket = Ticket.builder()
                    .title("Fix bug")
                    .key("B-1")
                    .type(TicketType.FEATURE)
                    .createdBy(user)
                    .board(board)
                    .build();
            ticket.setId(ticketId);

            TicketQueryRequest queryRequest = new TicketQueryRequest();

            TicketListResponse expectedResponse = mock(TicketListResponse.class);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            doNothing().when(ticketValidator).ensureTicketHasCorrectPlacement(
                    queryRequest.getSprintId(), queryRequest.getBacklogId());
            when(ticketRepository.findAll(any(Specification.class), any(Sort.class)))
                    .thenReturn(List.of(ticket));
            when(ticketMapper.toListResponse(ticket)).thenReturn(expectedResponse);

            // Act
            List<TicketListResponse> result = ticketQueryServiceImpl.listTickets(user, organizationId, boardId, queryRequest);

            // Assert
            assertNotNull(result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
            verify(ticketValidator).ensureTicketHasCorrectPlacement(
                    queryRequest.getSprintId(), queryRequest.getBacklogId());
        }

        @Test
        void includeAllBoards_true_noRelatedBoards_onlyPrimaryBoardSentToSpecification() {
            // Arrange
            UUID boardId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().id(boardId).build();

            TicketQueryRequest queryRequest = new TicketQueryRequest();
            queryRequest.setSprintId(UUID.randomUUID());
            queryRequest.setIncludeAllBoards(true);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER))
                    .thenReturn(board);
            doNothing().when(ticketValidator).ensureTicketHasCorrectPlacement(any(), any());
            when(boardHelper.resolveRelatedBoardEntities(board, user.getId()))
                    .thenReturn(Collections.emptyList());
            when(ticketRepository.findAll(any(Specification.class), any(Sort.class)))
                    .thenReturn(List.of());

            // Act
            List<TicketListResponse> result = ticketQueryServiceImpl.listTickets(user, organizationId, boardId, queryRequest);

            // Assert
            assertNotNull(result);
            verify(boardHelper).resolveRelatedBoardEntities(board, user.getId());
            verify(ticketRepository).findAll(any(Specification.class), any(Sort.class));
        }

        @Test
        void includeAllBoards_true_withRelatedBoards_primaryAndRelatedBoardsIncluded() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID childBoardId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().id(boardId).build();
            Board childBoard = Board.builder().id(childBoardId).build();

            TicketQueryRequest queryRequest = new TicketQueryRequest();
            queryRequest.setSprintId(UUID.randomUUID());
            queryRequest.setIncludeAllBoards(true);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER))
                    .thenReturn(board);
            doNothing().when(ticketValidator).ensureTicketHasCorrectPlacement(any(), any());
            when(boardHelper.resolveRelatedBoardEntities(board, user.getId()))
                    .thenReturn(List.of(childBoard));
            when(ticketRepository.findAll(any(Specification.class), any(Sort.class)))
                    .thenReturn(List.of());

            // Act
            List<TicketListResponse> result = ticketQueryServiceImpl.listTickets(user, organizationId, boardId, queryRequest);

            // Assert
            assertNotNull(result);
            verify(boardHelper).resolveRelatedBoardEntities(board, user.getId());
            verify(ticketRepository).findAll(any(Specification.class), any(Sort.class));
        }

        @Test
        void includeAllBoards_false_resolveRelatedBoardEntitiesNotCalled() {
            // Arrange
            UUID boardId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().id(boardId).build();

            TicketQueryRequest queryRequest = new TicketQueryRequest();
            queryRequest.setSprintId(UUID.randomUUID());
            queryRequest.setIncludeAllBoards(false);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER))
                    .thenReturn(board);
            doNothing().when(ticketValidator).ensureTicketHasCorrectPlacement(any(), any());
            when(ticketRepository.findAll(any(Specification.class), any(Sort.class)))
                    .thenReturn(List.of());

            // Act
            ticketQueryServiceImpl.listTickets(user, organizationId, boardId, queryRequest);

            // Assert
            verifyNoInteractions(boardHelper);
        }
    }

    @Nested
    class ListAllTickets {

        @Test
        void userIsNotMemberOfAnyBoard_returnsEmptyList() {
            // Arrange
            UUID userId = UUID.randomUUID();
            AllTicketQueryRequest queryRequest = new AllTicketQueryRequest();

            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of());

            // Act
            List<AllTicketListResponse> result = ticketQueryServiceImpl.listAllTickets(userId, organizationId, queryRequest);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(boardMemberQueryService).getMemberBoardIds(userId, organizationId);
            verifyNoInteractions(ticketRepository);
        }

        @Test
        void requestedBoardIdsContainNonMemberBoard_intersectionFiltersToMemberOnly() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID memberBoardId = UUID.randomUUID();
            UUID nonMemberBoardId = UUID.randomUUID();

            AllTicketQueryRequest queryRequest = new AllTicketQueryRequest();
            queryRequest.setBoardIds(List.of(memberBoardId, nonMemberBoardId));

            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(memberBoardId));
            when(ticketRepository.findAll(any(Specification.class), any(Sort.class)))
                    .thenReturn(List.of());

            // Act
            List<AllTicketListResponse> result = ticketQueryServiceImpl.listAllTickets(userId, organizationId, queryRequest);

            // Assert
            assertNotNull(result);
            verify(boardMemberQueryService).getMemberBoardIds(userId, organizationId);
            verify(ticketRepository).findAll(any(Specification.class), any(Sort.class));
        }

        @Test
        void createdByIdsSet_queriesWithASpecificationFilteringOnTheCreatorOfMemberBoards() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID memberBoardId = UUID.randomUUID();
            UUID creatorId = UUID.randomUUID();

            AllTicketQueryRequest queryRequest = new AllTicketQueryRequest();
            queryRequest.setCreatedByIds(List.of(creatorId));

            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(memberBoardId));
            when(ticketRepository.findAll(any(Specification.class), any(Sort.class)))
                    .thenReturn(List.of());

            // Act
            List<AllTicketListResponse> result = ticketQueryServiceImpl.listAllTickets(userId, organizationId, queryRequest);

            // Assert
            assertTrue(result.isEmpty());
            ArgumentCaptor<Specification<Ticket>> specCaptor = ArgumentCaptor.forClass(Specification.class);
            verify(ticketRepository).findAll(specCaptor.capture(), any(Sort.class));
            Root<Ticket> root = mock(Root.class, RETURNS_DEEP_STUBS);
            specCaptor.getValue().toPredicate(root, mock(CriteriaQuery.class), mock(CriteriaBuilder.class));
            verify(root.get("board").get("id")).in(List.of(memberBoardId));
            verify(root.get("createdBy").get("id")).in(List.of(creatorId));
        }

        @Test
        void createdByIdsNullOrEmpty_queriesWithoutACreatorPredicate() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID memberBoardId = UUID.randomUUID();

            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(memberBoardId));
            when(ticketRepository.findAll(any(Specification.class), any(Sort.class)))
                    .thenReturn(List.of());

            AllTicketQueryRequest nullRequest = new AllTicketQueryRequest();
            AllTicketQueryRequest emptyRequest = new AllTicketQueryRequest();
            emptyRequest.setCreatedByIds(List.of());

            // Act
            ticketQueryServiceImpl.listAllTickets(userId, organizationId, nullRequest);
            ticketQueryServiceImpl.listAllTickets(userId, organizationId, emptyRequest);

            // Assert
            ArgumentCaptor<Specification<Ticket>> specCaptor = ArgumentCaptor.forClass(Specification.class);
            verify(ticketRepository, times(2)).findAll(specCaptor.capture(), any(Sort.class));
            for (Specification<Ticket> captured : specCaptor.getAllValues()) {
                Root<Ticket> root = mock(Root.class, RETURNS_DEEP_STUBS);
                captured.toPredicate(root, mock(CriteriaQuery.class), mock(CriteriaBuilder.class));
                verify(root, never()).get("createdBy");
            }
        }

        @Test
        void incidentTypeAndCreatorOnlyOnNonMemberBoard_returnsNothingWithoutQueryingTickets() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID memberBoardId = UUID.randomUUID();
            UUID nonMemberBoardId = UUID.randomUUID();

            AllTicketQueryRequest queryRequest = new AllTicketQueryRequest();
            queryRequest.setTypes(List.of(TicketType.INCIDENT));
            queryRequest.setCreatedByIds(List.of(UUID.randomUUID()));
            queryRequest.setBoardIds(List.of(nonMemberBoardId));

            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(memberBoardId));

            // Act
            List<AllTicketListResponse> result = ticketQueryServiceImpl.listAllTickets(userId, organizationId, queryRequest);

            // Assert
            assertTrue(result.isEmpty());
            verifyNoInteractions(ticketRepository);
        }
    }

    @Nested
    class GetTicket {

        @Test
        void userIsNotBoardMember_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER))
                    .thenThrow(new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    ticketQueryServiceImpl.getTicket(user, organizationId, boardId, ticketId));
        }

        @Test
        void userIsBoardMember_returnsTicketDetail() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().id(boardId).build();

            Ticket ticket = Ticket.builder()
                    .title("Fix bug")
                    .key("B-1")
                    .type(TicketType.FEATURE)
                    .createdBy(user)
                    .build();
            ticket.setId(ticketId);

            TicketDetailResponse expectedResponse = mock(TicketDetailResponse.class);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            when(ticketValidator.ensureTicketExists(boardId, ticketId)).thenReturn(ticket);
            when(ticketMapper.toDetailResponse(ticket)).thenReturn(expectedResponse);

            // Act
            TicketDetailResponse result = ticketQueryServiceImpl.getTicket(user, organizationId, boardId, ticketId);

            // Assert
            assertNotNull(result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
            verify(ticketValidator).ensureTicketExists(boardId, ticketId);
        }
    }

    @Nested
    class GetEntityById {

        @Test
        void liveTicket_isReturnedWithoutAnAccessCheck() {
            // Arrange
            UUID ticketId = UUID.randomUUID();
            Ticket ticket = Ticket.builder()
                    .title("Fix bug")
                    .key("KAGI-40")
                    .type(TicketType.FEATURE)
                    .build();
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

            // Act
            Ticket result = ticketQueryServiceImpl.getEntityById(ticketId);

            // Assert
            assertSame(ticket, result);
            verifyNoInteractions(boardAccessService, boardMemberQueryService);
        }

        @Test
        void missingTicket_throwsNotFoundException() {
            // Arrange
            UUID ticketId = UUID.randomUUID();
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class, () ->
                    ticketQueryServiceImpl.getEntityById(ticketId));
            assertEquals(ExceptionDomainKaizenError.NOT_TICKET_FOUND.getCode(), exception.getErrorCode());
        }
    }

    @Nested
    class GetTicketByKey {

        @Test
        void userIsNotBoardMember_throwsNotFoundException() {
            // Arrange
            UUID userId = UUID.randomUUID();

            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(UUID.randomUUID()));
            when(ticketRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    ticketQueryServiceImpl.getTicketByKey(userId, organizationId, "KAGI-40"));
            verifyNoInteractions(ticketMapper);
        }

        @Test
        void userIsBoardMember_returnsTicket() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();
            Ticket ticket = Ticket.builder()
                    .title("Fix bug")
                    .key("KAGI-40")
                    .type(TicketType.FEATURE)
                    .build();
            AllTicketListResponse expectedResponse = mock(AllTicketListResponse.class);

            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(boardId));
            when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
            when(ticketMapper.toAllTicketListResponse(ticket)).thenReturn(expectedResponse);

            // Act
            AllTicketListResponse result = ticketQueryServiceImpl.getTicketByKey(userId, organizationId, "KAGI-40");

            // Assert
            assertSame(expectedResponse, result);
            verify(boardMemberQueryService).getMemberBoardIds(userId, organizationId);
        }

        @Test
        void userMemberOfNoBoard_throwsNotFoundException() {
            // Arrange
            UUID userId = UUID.randomUUID();

            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(Collections.emptyList());
            when(ticketRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class, () ->
                    ticketQueryServiceImpl.getTicketByKey(userId, organizationId, "KAGI-40"));
            assertEquals(ExceptionDomainKaizenError.NOT_TICKET_FOUND.getCode(), exception.getErrorCode());
        }
    }

    @Nested
    class ListTicketsBoardIds {

        private User user;
        private Board primary;
        private Board child;
        private Board outside;

        @BeforeEach
        void setUp() {
            user = User.builder().build();
            user.setId(UUID.randomUUID());
            primary = Board.builder().id(UUID.randomUUID()).backlog(backlog()).build();
            child = Board.builder().id(UUID.randomUUID()).backlog(backlog()).build();
            outside = Board.builder().id(UUID.randomUUID()).backlog(backlog()).build();
            when(boardAccessService.verifyBoardAccess(organizationId, primary.getId(), user.getId(), BoardMemberRole.VIEWER))
                    .thenReturn(primary);
        }

        private Backlog backlog() {
            Backlog backlog = Backlog.builder().build();
            backlog.setId(UUID.randomUUID());
            return backlog;
        }

        private TicketQueryRequest request(boolean includeAllBoards, UUID... boardIds) {
            TicketQueryRequest request = new TicketQueryRequest();
            request.setSprintId(UUID.randomUUID());
            request.setIncludeAllBoards(includeAllBoards);
            request.setBoardIds(boardIds.length == 0 ? null : List.of(boardIds));
            return request;
        }

        private List<UUID> listAndCaptureBoardIds(TicketQueryRequest request, boolean relatedBoards) {
            if (relatedBoards) {
                when(boardHelper.resolveRelatedBoardEntities(primary, user.getId())).thenReturn(List.of(child));
            }
            try (MockedStatic<TicketSpecification> specification = mockStatic(TicketSpecification.class)) {
                specification.when(() -> TicketSpecification.filterBy(any(), any(), any()))
                        .thenReturn(mock(Specification.class));
                when(ticketRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

                ticketQueryServiceImpl.listTickets(user, organizationId, primary.getId(), request);

                ArgumentCaptor<List<UUID>> boardIds = ArgumentCaptor.forClass(List.class);
                specification.verify(() -> TicketSpecification.filterBy(boardIds.capture(), any(), eq(request)));
                return boardIds.getValue();
            }
        }

        @Test
        void noBoardIds_includeAllBoards_usesPrimaryAndRelatedBoards() {
            List<UUID> used = listAndCaptureBoardIds(request(true), true);

            assertThat(used).containsExactlyInAnyOrder(primary.getId(), child.getId());
        }

        @Test
        void boardIdsInsideRelatedBoards_usesOnlyThoseBoards() {
            List<UUID> used = listAndCaptureBoardIds(request(true, child.getId()), true);

            assertThat(used).containsExactly(child.getId());
        }

        @Test
        void boardIdsPartlyOutsideRelatedBoards_dropsTheOutsideIds() {
            List<UUID> used = listAndCaptureBoardIds(request(true, child.getId(), outside.getId()), true);

            assertThat(used).containsExactly(child.getId());
        }

        @Test
        void boardIdsAllOutsideRelatedBoards_returnsEmptyListWithoutQuerying() {
            when(boardHelper.resolveRelatedBoardEntities(primary, user.getId())).thenReturn(List.of(child));

            List<TicketListResponse> result = ticketQueryServiceImpl.listTickets(
                    user, organizationId, primary.getId(), request(true, outside.getId()));

            assertThat(result).isEmpty();
            verifyNoInteractions(ticketRepository);
        }

        @Test
        void includeAllBoardsOff_boardIdsOfThePrimaryBoard_usesIt() {
            List<UUID> used = listAndCaptureBoardIds(request(false, primary.getId()), false);

            assertThat(used).containsExactly(primary.getId());
        }

        @Test
        void includeAllBoardsOff_boardIdsOutsideThePrimaryBoard_returnsEmptyList() {
            List<TicketListResponse> result = ticketQueryServiceImpl.listTickets(
                    user, organizationId, primary.getId(), request(false, outside.getId()));

            assertThat(result).isEmpty();
            verifyNoInteractions(ticketRepository);
        }

        @Test
        void includeAllBoardsOff_partlyOutside_keepsOnlyThePrimaryBoard() {
            List<UUID> used = listAndCaptureBoardIds(request(false, primary.getId(), outside.getId()), false);

            assertThat(used).containsExactly(primary.getId());
        }

        @Test
        void backlogQuery_includeAll_backlogIdsFollowTheRetainedBoardsOnly() {
            when(boardHelper.resolveRelatedBoardEntities(primary, user.getId())).thenReturn(List.of(child));
            TicketQueryRequest request = new TicketQueryRequest();
            request.setBacklogId(child.getBacklog().getId());
            request.setIncludeAllBoards(true);
            request.setBoardIds(List.of(child.getId()));

            try (MockedStatic<TicketSpecification> specification = mockStatic(TicketSpecification.class)) {
                specification.when(() -> TicketSpecification.filterBy(any(), any(), any()))
                        .thenReturn(mock(Specification.class));
                when(ticketRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

                ticketQueryServiceImpl.listTickets(user, organizationId, primary.getId(), request);

                ArgumentCaptor<List<UUID>> backlogIds = ArgumentCaptor.forClass(List.class);
                specification.verify(() -> TicketSpecification.filterBy(any(), backlogIds.capture(), eq(request)));
                assertThat(backlogIds.getValue()).containsExactly(child.getBacklog().getId());
            }
        }
    }

    @Nested
    class GetAccessibleTickets {

        @Test
        void nullOrEmptyIds_returnsEmptyListWithoutQuerying() {
            UUID userId = UUID.randomUUID();

            assertThat(ticketQueryServiceImpl.getAccessibleTickets(userId, organizationId, null)).isEmpty();
            assertThat(ticketQueryServiceImpl.getAccessibleTickets(userId, organizationId, List.of())).isEmpty();

            verifyNoInteractions(boardMemberQueryService, ticketRepository);
        }

        @Test
        void userWithoutBoards_returnsEmptyListWithoutQueryingTickets() {
            UUID userId = UUID.randomUUID();
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of());

            assertThat(ticketQueryServiceImpl.getAccessibleTickets(userId, organizationId, List.of(UUID.randomUUID()))).isEmpty();

            verifyNoInteractions(ticketRepository);
        }

        @Test
        void memberBoards_scopesTheQueryToMemberBoardsAndRequestedIds() {
            UUID userId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();
            UUID ticketId = UUID.randomUUID();
            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.FEATURE).build();
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(boardId));
            try (MockedStatic<TicketSpecification> specification = mockStatic(TicketSpecification.class)) {
                Specification<Ticket> built = mock(Specification.class);
                specification.when(() -> TicketSpecification.filterByBoardsAndIds(List.of(boardId), List.of(ticketId)))
                        .thenReturn(built);
                when(ticketRepository.findAll(built)).thenReturn(List.of(ticket));

                List<Ticket> result = ticketQueryServiceImpl.getAccessibleTickets(userId, organizationId, List.of(ticketId));

                assertThat(result).containsExactly(ticket);
            }
        }
    }

    @Nested
    class GetAccessibleTicketsByKeys {

        @Test
        void nullOrEmptyKeys_returnsEmptyListWithoutQuerying() {
            UUID userId = UUID.randomUUID();

            assertThat(ticketQueryServiceImpl.getAccessibleTicketsByKeys(userId, organizationId, null)).isEmpty();
            assertThat(ticketQueryServiceImpl.getAccessibleTicketsByKeys(userId, organizationId, List.of())).isEmpty();

            verifyNoInteractions(boardMemberQueryService, ticketRepository);
        }

        @Test
        void userWithoutBoards_returnsEmptyListWithoutQueryingTickets() {
            UUID userId = UUID.randomUUID();
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of());

            assertThat(ticketQueryServiceImpl.getAccessibleTicketsByKeys(userId, organizationId, List.of("B-1"))).isEmpty();

            verifyNoInteractions(ticketRepository);
        }

        @Test
        void memberBoards_scopesTheQueryToMemberBoardsAndRequestedKeys() {
            UUID userId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();
            List<String> keys = List.of("B-1", "B-2");
            Ticket ticket = Ticket.builder().title("T").key("B-1").type(TicketType.TASK).build();
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(boardId));
            try (MockedStatic<TicketSpecification> specification = mockStatic(TicketSpecification.class)) {
                Specification<Ticket> built = mock(Specification.class);
                specification.when(() -> TicketSpecification.filterByBoardsAndKeys(List.of(boardId), keys))
                        .thenReturn(built);
                when(ticketRepository.findAll(built)).thenReturn(List.of(ticket));

                List<Ticket> result = ticketQueryServiceImpl.getAccessibleTicketsByKeys(userId, organizationId, keys);

                assertThat(result).containsExactly(ticket);
            }
        }
    }

    @Nested
    class ListActiveAssignedTickets {

        @Test
        void userWithoutBoards_returnsEmptyListWithoutQuerying() {
            UUID userId = UUID.randomUUID();
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of());

            assertThat(ticketQueryServiceImpl.listActiveAssignedTickets(userId, organizationId, 30)).isEmpty();

            verifyNoInteractions(ticketRepository);
        }

        @Test
        void memberBoards_queriesInProgressTicketsOfTheUserFirstPageSortedByKey() {
            UUID userId = UUID.randomUUID();
            UUID boardId = UUID.randomUUID();
            Ticket subTicket = Ticket.builder().title("Sub").key("B-2").type(TicketType.FEATURE).build();
            AllTicketListResponse mapped = mock(AllTicketListResponse.class);
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of(boardId));
            when(ticketMapper.toAllTicketListResponse(subTicket)).thenReturn(mapped);
            try (MockedStatic<TicketSpecification> specification = mockStatic(TicketSpecification.class)) {
                Specification<Ticket> built = mock(Specification.class);
                specification.when(() -> TicketSpecification.filterByBoardsAssigneeAndStatuses(
                        List.of(boardId), userId, List.of(TicketStatus.IN_PROGRESS))).thenReturn(built);
                when(ticketRepository.findAll(eq(built), any(Pageable.class)))
                        .thenReturn(new PageImpl<>(List.of(subTicket)));

                List<AllTicketListResponse> result = ticketQueryServiceImpl.listActiveAssignedTickets(userId, organizationId, 30);

                assertThat(result).containsExactly(mapped);
                ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
                verify(ticketRepository).findAll(eq(built), pageable.capture());
                assertThat(pageable.getValue().getPageNumber()).isZero();
                assertThat(pageable.getValue().getPageSize()).isEqualTo(30);
                assertThat(pageable.getValue().getSort().getOrderFor("key").getDirection())
                        .isEqualTo(Sort.Direction.ASC);
            }
        }
    }

    @Nested
    class GetHomeSummary {

        @BeforeEach
        void stubMapper() {
            // The mapper only carries the two counts into the record, so the stub mirrors it.
            when(ticketMapper.toHomeSummaryResponse(anyLong(), anyLong())).thenAnswer(
                    invocation -> new HomeSummaryResponse(invocation.getArgument(0), invocation.getArgument(1)));
        }

        @Test
        void userWithoutBoards_returnsZeroCountsWithoutQuerying() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(List.of());

            // Act
            HomeSummaryResponse result = ticketQueryServiceImpl.getHomeSummary(userId, organizationId);

            // Assert
            assertEquals(0L, result.openIncidents());
            assertEquals(0L, result.openSprintTickets());
            verifyNoInteractions(ticketRepository, sprintQueryService);
        }

        @Test
        void memberBoardsWithActiveSprints_returnsOpenIncidentAndOpenSprintTicketCounts() {
            // Arrange
            UUID userId = UUID.randomUUID();
            List<UUID> memberBoardIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            List<UUID> activeSprintIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(memberBoardIds);
            when(ticketRepository.countRootsByBoardIdsAndTypeAndStatusNot(
                    memberBoardIds, TicketType.INCIDENT, TicketStatus.DONE)).thenReturn(3L);
            when(sprintQueryService.getActiveSprintIdsByBoardIds(memberBoardIds)).thenReturn(activeSprintIds);
            when(ticketRepository.countSprintListRootsByBoardIdsAndSprintIdsAndStatusNot(
                    memberBoardIds, activeSprintIds, TicketStatus.DONE)).thenReturn(17L);

            // Act
            HomeSummaryResponse result = ticketQueryServiceImpl.getHomeSummary(userId, organizationId);

            // Assert
            assertEquals(3L, result.openIncidents());
            assertEquals(17L, result.openSprintTickets());
        }

        @Test
        void memberBoardsWithoutActiveSprint_returnsZeroSprintTicketsWithoutCountingThem() {
            // Arrange
            UUID userId = UUID.randomUUID();
            List<UUID> memberBoardIds = List.of(UUID.randomUUID());
            when(boardMemberQueryService.getMemberBoardIds(userId, organizationId)).thenReturn(memberBoardIds);
            when(ticketRepository.countRootsByBoardIdsAndTypeAndStatusNot(
                    memberBoardIds, TicketType.INCIDENT, TicketStatus.DONE)).thenReturn(2L);
            when(sprintQueryService.getActiveSprintIdsByBoardIds(memberBoardIds)).thenReturn(List.of());

            // Act
            HomeSummaryResponse result = ticketQueryServiceImpl.getHomeSummary(userId, organizationId);

            // Assert
            assertEquals(2L, result.openIncidents());
            assertEquals(0L, result.openSprintTickets());
            verify(ticketRepository, never())
                    .countSprintListRootsByBoardIdsAndSprintIdsAndStatusNot(any(), any(), any());
        }
    }

    @Nested
    class GetDoneActiveSprintTicketsByAssignees {

        private TicketAssignee assignment(User user, Ticket ticket) {
            return TicketAssignee.builder().user(user).ticket(ticket).build();
        }

        private User user() {
            User user = User.builder().build();
            user.setId(UUID.randomUUID());
            return user;
        }

        @Test
        void noMemberBoards_returnsEmpty() {
            // Arrange
            UUID readerId = UUID.randomUUID();
            when(boardMemberQueryService.getMemberBoardIds(readerId, organizationId)).thenReturn(List.of());

            // Act
            Map<UUID, List<Ticket>> result = ticketQueryServiceImpl.getDoneActiveSprintTicketsByAssignees(readerId,
                    organizationId,
                    Set.of(UUID.randomUUID()));

            // Assert
            assertTrue(result.isEmpty());
            verifyNoInteractions(sprintQueryService, ticketAssigneeRepository);
        }

        @Test
        void noActiveSprints_returnsEmptyWithoutQuery() {
            // Arrange
            UUID readerId = UUID.randomUUID();
            List<UUID> boardIds = List.of(UUID.randomUUID());
            when(boardMemberQueryService.getMemberBoardIds(readerId, organizationId)).thenReturn(boardIds);
            when(sprintQueryService.getActiveSprintIdsByBoardIds(boardIds)).thenReturn(List.of());

            // Act
            Map<UUID, List<Ticket>> result = ticketQueryServiceImpl.getDoneActiveSprintTicketsByAssignees(readerId,
                    organizationId,
                    Set.of(UUID.randomUUID()));

            // Assert
            assertTrue(result.isEmpty());
            verifyNoInteractions(ticketAssigneeRepository);
        }

        @Test
        void emptyAssignees_returnsEmptyWithoutLookups() {
            // Act
            Map<UUID, List<Ticket>> result = ticketQueryServiceImpl
                    .getDoneActiveSprintTicketsByAssignees(UUID.randomUUID(), organizationId, Set.of());

            // Assert
            assertTrue(result.isEmpty());
            verifyNoInteractions(boardMemberQueryService, sprintQueryService, ticketAssigneeRepository);
        }

        @Test
        void ticketOfTwoAssignees_listedUnderBoth() {
            // Arrange
            UUID readerId = UUID.randomUUID();
            List<UUID> boardIds = List.of(UUID.randomUUID());
            List<UUID> sprintIds = List.of(UUID.randomUUID());
            User ada = user();
            User alan = user();
            Ticket shared = Ticket.builder().key("B-1").build();
            Ticket adaOnly = Ticket.builder().key("B-2").build();
            Set<UUID> assigneeIds = Set.of(ada.getId(), alan.getId());
            when(boardMemberQueryService.getMemberBoardIds(readerId, organizationId)).thenReturn(boardIds);
            when(sprintQueryService.getActiveSprintIdsByBoardIds(boardIds)).thenReturn(sprintIds);
            when(ticketAssigneeRepository.findByAssigneesBoardsSprintsAndStatus(assigneeIds, boardIds, sprintIds,
                    TicketStatus.DONE)).thenReturn(List.of(assignment(ada, shared), assignment(alan, shared),
                    assignment(ada, adaOnly)));

            // Act
            Map<UUID, List<Ticket>> result = ticketQueryServiceImpl.getDoneActiveSprintTicketsByAssignees(readerId,
                    organizationId,
                    assigneeIds);

            // Assert
            assertEquals(List.of(shared, adaOnly), result.get(ada.getId()));
            assertEquals(List.of(shared), result.get(alan.getId()));
            assertEquals(2, result.size());
        }

        @Test
        void queriesDoneOnly() {
            // Arrange
            UUID readerId = UUID.randomUUID();
            List<UUID> boardIds = List.of(UUID.randomUUID());
            List<UUID> sprintIds = List.of(UUID.randomUUID());
            Set<UUID> assigneeIds = Set.of(UUID.randomUUID());
            when(boardMemberQueryService.getMemberBoardIds(readerId, organizationId)).thenReturn(boardIds);
            when(sprintQueryService.getActiveSprintIdsByBoardIds(boardIds)).thenReturn(sprintIds);
            when(ticketAssigneeRepository.findByAssigneesBoardsSprintsAndStatus(any(), any(), any(), any()))
                    .thenReturn(List.of());

            // Act
            ticketQueryServiceImpl.getDoneActiveSprintTicketsByAssignees(readerId, organizationId, assigneeIds);

            // Assert
            verify(ticketAssigneeRepository).findByAssigneesBoardsSprintsAndStatus(assigneeIds, boardIds, sprintIds,
                    TicketStatus.DONE);
        }
    }
}
