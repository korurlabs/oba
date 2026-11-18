package com.korur.oba.apps.kazan.board.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.BoardMember;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.repository.BoardMemberRepository;
import com.korur.oba.apps.kazan.board.repository.BoardMemberSpecialtyRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.mapper.BoardMemberMapper;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class BoardMemberQueryServiceImplUnitMemberUserIdsTest {

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

    @InjectMocks
    private BoardMemberQueryServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();

    private BoardMember member(UUID userId, BoardMemberRole role) {
        User user = User.builder().build();
        user.setId(userId);
        Board board = Board.builder().organizationId(organizationId).build();
        board.setId(UUID.randomUUID());
        return BoardMember.builder().board(board).user(user).role(role).build();
    }

    private void organizationHasActiveUsers(UUID... userIds) {
        when(organizationMemberQueryService.findActiveUserIdsByRoles(eq(organizationId), any()))
                .thenReturn(Set.of(userIds));
    }

    @Test
    void getMemberUserIds_noBoards_returnsEmptyWithoutQuery() {
        // Act
        List<UUID> result = service.getMemberUserIds(Set.of());

        // Assert
        assertThat(result).isEmpty();
        verifyNoInteractions(boardMemberRepository);
    }

    @Test
    void getMemberUserIds_userOnTwoBoards_returnsUserOnce() {
        // Arrange
        UUID boardA = UUID.randomUUID();
        UUID boardB = UUID.randomUUID();
        UUID shared = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Set<UUID> boardIds = Set.of(boardA, boardB);
        organizationHasActiveUsers(shared, other);
        when(boardMemberRepository.findByBoard_IdIn(boardIds)).thenReturn(List.of(
                member(shared, BoardMemberRole.MEMBER),
                member(other, BoardMemberRole.VIEWER),
                member(shared, BoardMemberRole.ADMIN)));

        // Act
        List<UUID> result = service.getMemberUserIds(boardIds);

        // Assert
        assertThat(result).containsExactlyInAnyOrder(shared, other);
        verify(boardMemberRepository).findByBoard_IdIn(boardIds);
    }

    @Test
    void getMemberUserIds_everyRole_includesViewersAndOwners() {
        // Arrange
        UUID viewer = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Set<UUID> boardIds = Set.of(UUID.randomUUID());
        organizationHasActiveUsers(viewer, owner);
        when(boardMemberRepository.findByBoard_IdIn(boardIds)).thenReturn(List.of(
                member(viewer, BoardMemberRole.VIEWER), member(owner, BoardMemberRole.OWNER)));

        // Act
        List<UUID> result = service.getMemberUserIds(boardIds);

        // Assert
        assertThat(result).containsExactlyInAnyOrder(viewer, owner);
    }

    @Test
    void getMemberUserIds_userNoLongerActiveInOrganization_isLeftOut() {
        // Arrange
        UUID active = UUID.randomUUID();
        UUID departed = UUID.randomUUID();
        Set<UUID> boardIds = Set.of(UUID.randomUUID());
        organizationHasActiveUsers(active);
        when(boardMemberRepository.findByBoard_IdIn(boardIds)).thenReturn(List.of(
                member(active, BoardMemberRole.MEMBER), member(departed, BoardMemberRole.ADMIN)));

        // Act
        List<UUID> result = service.getMemberUserIds(boardIds);

        // Assert
        assertThat(result).containsExactly(active);
    }
}
