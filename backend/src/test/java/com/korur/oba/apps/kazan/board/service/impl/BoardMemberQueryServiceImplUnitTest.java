package com.korur.oba.apps.kazan.board.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.dto.response.BoardMemberCandidateResponse;
import com.korur.oba.apps.kazan.board.dto.response.BoardMemberResponse;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardMember;
import com.korur.oba.apps.kazan.board.entity.BoardMemberSpecialty;
import com.korur.oba.apps.kazan.board.enums.BoardMemberSpecialtyType;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.mapper.BoardMemberMapper;
import com.korur.oba.apps.kazan.board.repository.BoardMemberRepository;
import com.korur.oba.apps.kazan.board.repository.BoardMemberSpecialtyRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberKindSummaryProjection;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class BoardMemberQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private BoardMemberRepository boardMemberRepository;

    @Mock
    private BoardMemberSpecialtyRepository boardMemberSpecialtyRepository;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private BoardMemberMapper boardMemberMapper;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @Mock
    private Clock clock;

    @InjectMocks
    private BoardMemberQueryServiceImpl boardMemberQueryServiceImpl;

    @Nested
    class ListMembers {

        @Test
        void userIsNotBoardMember_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER))
                    .thenThrow(new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    boardMemberQueryServiceImpl.listMembers(user, organizationId, boardId));
        }

        @Test
        void userIsBoardMember_returnsMemberList() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            BoardMember member = BoardMember.builder()
                    .board(board)
                    .user(user)
                    .role(BoardMemberRole.MEMBER)
                    .build();
            member.setId(memberId);

            BoardMemberResponse expectedResponse = new BoardMemberResponse(
                    memberId, null, null, null, null, BoardMemberRole.MEMBER, null, true, UserKind.HUMAN, null, null, null);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            when(organizationMemberQueryService.findActiveUserIdsByRoles(eq(organizationId), any()))
                    .thenReturn(Set.of(user.getId()));
            when(boardMemberRepository.findByBoard_Id(boardId)).thenReturn(List.of(member));
            when(appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.KAZAN, List.of(user.getId())))
                    .thenReturn(Set.of(user.getId()));
            when(boardMemberMapper.toResponse(member, true)).thenReturn(expectedResponse);

            // Act
            List<BoardMemberResponse> result = boardMemberQueryServiceImpl.listMembers(user, organizationId, boardId);

            // Assert
            assertEquals(List.of(expectedResponse), result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
            verify(boardMemberRepository).findByBoard_Id(boardId);
        }

        @Test
        void memberNoLongerActiveInOrganization_isLeftOut() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User reader = User.builder().build();
            reader.setId(UUID.randomUUID());
            User departed = User.builder().build();
            departed.setId(UUID.randomUUID());
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            BoardMember readerMembership = BoardMember.builder().board(board).user(reader)
                    .role(BoardMemberRole.ADMIN).build();
            BoardMember departedMembership = BoardMember.builder().board(board).user(departed)
                    .role(BoardMemberRole.MEMBER).build();
            BoardMemberResponse readerResponse = new BoardMemberResponse(
                    UUID.randomUUID(), null, null, null, null, BoardMemberRole.ADMIN, null, true, UserKind.HUMAN, null, null, null);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, reader.getId(),
                    BoardMemberRole.VIEWER)).thenReturn(board);
            when(organizationMemberQueryService.findActiveUserIdsByRoles(eq(organizationId), any()))
                    .thenReturn(Set.of(reader.getId()));
            when(boardMemberRepository.findByBoard_Id(boardId))
                    .thenReturn(List.of(readerMembership, departedMembership));
            when(appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.KAZAN, List.of(reader.getId())))
                    .thenReturn(Set.of(reader.getId()));
            when(boardMemberMapper.toResponse(readerMembership, true)).thenReturn(readerResponse);

            // Act
            List<BoardMemberResponse> result = boardMemberQueryServiceImpl.listMembers(reader, organizationId, boardId);

            // Assert
            assertEquals(List.of(readerResponse), result);
            verify(boardMemberMapper, never()).toResponse(eq(departedMembership), anyBoolean());
        }

        @Test
        void memberWithoutKazan_isListedWithKazanAccessFalse() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User reader = User.builder().build();
            reader.setId(UUID.randomUUID());
            User withoutKazan = User.builder().build();
            withoutKazan.setId(UUID.randomUUID());
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            BoardMember readerMembership = BoardMember.builder().board(board).user(reader)
                    .role(BoardMemberRole.ADMIN).build();
            BoardMember withoutKazanMembership = BoardMember.builder().board(board).user(withoutKazan)
                    .role(BoardMemberRole.MEMBER).build();
            BoardMemberResponse readerResponse = new BoardMemberResponse(
                    UUID.randomUUID(), reader.getId(), null, null, null, BoardMemberRole.ADMIN, null, true, UserKind.HUMAN, null, null, null);
            BoardMemberResponse withoutKazanResponse = new BoardMemberResponse(
                    UUID.randomUUID(), withoutKazan.getId(), null, null, null, BoardMemberRole.MEMBER, null, false, UserKind.HUMAN, null, null, null);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, reader.getId(),
                    BoardMemberRole.VIEWER)).thenReturn(board);
            when(organizationMemberQueryService.findActiveUserIdsByRoles(eq(organizationId), any()))
                    .thenReturn(Set.of(reader.getId(), withoutKazan.getId()));
            when(boardMemberRepository.findByBoard_Id(boardId))
                    .thenReturn(List.of(readerMembership, withoutKazanMembership));
            when(appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.KAZAN,
                    List.of(reader.getId(), withoutKazan.getId()))).thenReturn(Set.of(reader.getId()));
            when(boardMemberMapper.toResponse(readerMembership, true)).thenReturn(readerResponse);
            when(boardMemberMapper.toResponse(withoutKazanMembership, false)).thenReturn(withoutKazanResponse);

            // Act
            List<BoardMemberResponse> result = boardMemberQueryServiceImpl.listMembers(reader, organizationId, boardId);

            // Assert
            assertEquals(List.of(readerResponse, withoutKazanResponse), result);
        }
    }

    @Nested
    class ListMemberCandidates {

        private OrganizationMemberKindSummaryProjection summary(UUID userId) {
            OrganizationMemberKindSummaryProjection summary = mock(OrganizationMemberKindSummaryProjection.class);
            when(summary.getUserId()).thenReturn(userId);
            return summary;
        }

        @Test
        void boardAdmin_listsOrganizationMembersWithKazanAccessInNameOrder() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User admin = User.builder().build();
            admin.setId(UUID.randomUUID());
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            UUID zoeId = UUID.randomUUID();
            UUID adaId = UUID.randomUUID();
            OrganizationMemberKindSummaryProjection zoe = summary(zoeId);
            OrganizationMemberKindSummaryProjection ada = summary(adaId);
            BoardMemberCandidateResponse zoeResponse = new BoardMemberCandidateResponse(
                    zoeId, "zoe@example.com", "Zoe", "Last", OrganizationMemberRole.MEMBER, true, UserKind.HUMAN, null, null, null);
            BoardMemberCandidateResponse adaResponse = new BoardMemberCandidateResponse(
                    adaId, "ada@example.com", "ada", "Last", OrganizationMemberRole.GUEST, false, UserKind.HUMAN, null, null, null);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, admin.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(board);
            when(clock.instant()).thenReturn(Instant.parse("2026-11-11T10:00:00Z"));
            when(organizationMemberQueryService.listActiveMemberKindSummaries(organizationId,
                    Instant.parse("2026-11-11T10:00:00Z"))).thenReturn(List.of(zoe, ada));
            when(appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.KAZAN, List.of(zoeId, adaId)))
                    .thenReturn(Set.of(zoeId));
            when(boardMemberMapper.toCandidateResponse(zoe, true)).thenReturn(zoeResponse);
            when(boardMemberMapper.toCandidateResponse(ada, false)).thenReturn(adaResponse);

            // Act
            List<BoardMemberCandidateResponse> result =
                    boardMemberQueryServiceImpl.listMemberCandidates(admin, organizationId, boardId);

            // Assert
            assertEquals(List.of(adaResponse, zoeResponse), result);
        }

        @Test
        void agentInOrganization_isOfferedWithItsKindFromTheKindSummaries() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User admin = User.builder().build();
            admin.setId(UUID.randomUUID());
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            UUID agentId = UUID.randomUUID();
            OrganizationMemberKindSummaryProjection agent = summary(agentId);
            BoardMemberCandidateResponse agentResponse = new BoardMemberCandidateResponse(agentId,
                    "agent@agents.oba.invalid", "Reviewer", null, OrganizationMemberRole.MEMBER, true,
                    UserKind.AGENT, AgentRole.CYBERSECURITY_ENGINEER, AgentFlow.ROUTINE, AgentProvider.ANTHROPIC);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, admin.getId(), BoardMemberRole.ADMIN))
                    .thenReturn(board);
            when(clock.instant()).thenReturn(Instant.parse("2026-11-11T10:00:00Z"));
            when(organizationMemberQueryService.listActiveMemberKindSummaries(organizationId,
                    Instant.parse("2026-11-11T10:00:00Z"))).thenReturn(List.of(agent));
            when(appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.KAZAN, List.of(agentId)))
                    .thenReturn(Set.of(agentId));
            when(boardMemberMapper.toCandidateResponse(agent, true)).thenReturn(agentResponse);

            // Act
            List<BoardMemberCandidateResponse> result =
                    boardMemberQueryServiceImpl.listMemberCandidates(admin, organizationId, boardId);

            // Assert
            assertEquals(List.of(agentResponse), result);
            verify(organizationMemberQueryService, never()).listActiveMemberSummaries(any(), any());
        }

        @Test
        void callerBelowBoardAdmin_isRefusedBeforeAnyRead() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User viewer = User.builder().build();
            viewer.setId(UUID.randomUUID());
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, viewer.getId(), BoardMemberRole.ADMIN))
                    .thenThrow(new NotFoundException(ExceptionNotFoundError.RESOURCE_FOUND));

            // Act & Assert
            assertThrows(NotFoundException.class, () ->
                    boardMemberQueryServiceImpl.listMemberCandidates(viewer, organizationId, boardId));
            verifyNoInteractions(organizationMemberQueryService, appAccessQueryService);
        }
    }

    @Nested
    class FindMembersByBoardIdAndSpecialty {

        @Test
        void specialtyHolderNoLongerActiveInOrganization_isLeftOut() {
            // Arrange
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(UUID.randomUUID());
            User active = User.builder().build();
            active.setId(UUID.randomUUID());
            User departed = User.builder().build();
            departed.setId(UUID.randomUUID());
            BoardMember activeMember = BoardMember.builder().board(board).user(active)
                    .role(BoardMemberRole.MEMBER).build();
            BoardMember departedMember = BoardMember.builder().board(board).user(departed)
                    .role(BoardMemberRole.MEMBER).build();
            BoardMemberSpecialty activeSpecialty = BoardMemberSpecialty.builder().boardMember(activeMember).build();
            BoardMemberSpecialty departedSpecialty = BoardMemberSpecialty.builder().boardMember(departedMember).build();
            when(boardMemberSpecialtyRepository.findByBoardMember_Board_IdAndSpecialty(
                    board.getId(), BoardMemberSpecialtyType.DEVELOPER))
                    .thenReturn(List.of(activeSpecialty, departedSpecialty));
            when(organizationMemberQueryService.findActiveUserIdsByRoles(eq(organizationId), any()))
                    .thenReturn(Set.of(active.getId()));

            // Act
            List<BoardMember> result = boardMemberQueryServiceImpl.findMembersByBoardIdAndSpecialty(
                    board.getId(), BoardMemberSpecialtyType.DEVELOPER);

            // Assert
            assertEquals(List.of(activeMember), result);
        }

        @Test
        void noSpecialtyHolders_returnsEmptyWithoutOrganizationLookup() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            when(boardMemberSpecialtyRepository.findByBoardMember_Board_IdAndSpecialty(
                    boardId, BoardMemberSpecialtyType.DEVELOPER)).thenReturn(List.of());

            // Act
            List<BoardMember> result = boardMemberQueryServiceImpl.findMembersByBoardIdAndSpecialty(
                    boardId, BoardMemberSpecialtyType.DEVELOPER);

            // Assert
            assertTrue(result.isEmpty());
            verifyNoInteractions(organizationMemberQueryService);
        }
    }

    @Nested
    class GetMemberBoardIds {

        @Test
        void userWithMemberships_returnsCorrectBoardIds() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID boardId1 = UUID.randomUUID();
            UUID boardId2 = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(userId);

            Board board1 = Board.builder().organizationId(organizationId).name("Board 1").prefix("A").build();
            board1.setId(boardId1);

            Board board2 = Board.builder().organizationId(organizationId).name("Board 2").prefix("B").build();
            board2.setId(boardId2);

            BoardMember member1 = BoardMember.builder().board(board1).user(user).role(BoardMemberRole.MEMBER).build();
            member1.setId(UUID.randomUUID());

            BoardMember member2 = BoardMember.builder().board(board2).user(user).role(BoardMemberRole.MEMBER).build();
            member2.setId(UUID.randomUUID());

            when(boardMemberRepository.findByUser_IdAndBoard_OrganizationId(userId, organizationId)).thenReturn(List.of(member1, member2));

            // Act
            List<UUID> result = boardMemberQueryServiceImpl.getMemberBoardIds(userId, organizationId);

            // Assert
            assertEquals(2, result.size());
            assertTrue(result.contains(boardId1));
            assertTrue(result.contains(boardId2));
        }

        @Test
        void userWithNoMemberships_returnsEmptyList() {
            // Arrange
            UUID userId = UUID.randomUUID();

            when(boardMemberRepository.findByUser_IdAndBoard_OrganizationId(userId, organizationId)).thenReturn(Collections.emptyList());

            // Act
            List<UUID> result = boardMemberQueryServiceImpl.getMemberBoardIds(userId, organizationId);

            // Assert
            assertTrue(result.isEmpty());
        }
    }
}
