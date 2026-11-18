package com.korur.oba.apps.kaizen.board.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.entity.BoardMember;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.repository.BoardMemberRepository;

@ExtendWith(MockitoExtension.class)
class BoardMemberQueryServiceImplUnitAdminTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private BoardMemberRepository boardMemberRepository;

    @InjectMocks
    private BoardMemberQueryServiceImpl boardMemberQueryServiceImpl;

    private static BoardMember membership(Board board, BoardMemberRole role) {
        return BoardMember.builder().board(board).role(role).build();
    }

    private static Board board() {
        return Board.builder().id(UUID.randomUUID()).build();
    }

    @Nested
    class GetAdminBoardIds {

        @Test
        void mixedRoles_returnsOnlyBoardsWhereTheUserIsOwnerOrAdmin() {
            UUID userId = UUID.randomUUID();
            Board owned = board();
            Board administered = board();
            Board contributed = board();
            Board viewed = board();
            when(boardMemberRepository.findByUser_IdAndBoard_OrganizationId(userId, organizationId)).thenReturn(List.of(
                    membership(owned, BoardMemberRole.OWNER),
                    membership(contributed, BoardMemberRole.MEMBER),
                    membership(administered, BoardMemberRole.ADMIN),
                    membership(viewed, BoardMemberRole.VIEWER)));

            List<UUID> result = boardMemberQueryServiceImpl.getAdminBoardIds(userId, organizationId);

            assertThat(result).containsExactly(owned.getId(), administered.getId());
        }

        @Test
        void memberAndViewerOnly_returnsEmptyList() {
            UUID userId = UUID.randomUUID();
            when(boardMemberRepository.findByUser_IdAndBoard_OrganizationId(userId, organizationId)).thenReturn(List.of(
                    membership(board(), BoardMemberRole.MEMBER),
                    membership(board(), BoardMemberRole.VIEWER)));

            assertThat(boardMemberQueryServiceImpl.getAdminBoardIds(userId, organizationId)).isEmpty();
        }

        @Test
        void noMemberships_returnsEmptyList() {
            UUID userId = UUID.randomUUID();
            when(boardMemberRepository.findByUser_IdAndBoard_OrganizationId(userId, organizationId)).thenReturn(List.of());

            assertThat(boardMemberQueryServiceImpl.getAdminBoardIds(userId, organizationId)).isEmpty();
        }
    }
}
