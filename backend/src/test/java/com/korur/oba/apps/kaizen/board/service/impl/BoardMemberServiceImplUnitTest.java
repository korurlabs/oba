package com.korur.oba.apps.kaizen.board.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.dto.request.BoardMemberAddRequest;
import com.korur.oba.apps.kaizen.board.dto.request.BoardMemberUpdateRequest;
import com.korur.oba.apps.kaizen.board.dto.response.BoardMemberResponse;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.entity.BoardMember;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.mapper.BoardMemberMapper;
import com.korur.oba.apps.kaizen.board.repository.BoardMemberRepository;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.board.validator.BoardValidator;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;

@ExtendWith(MockitoExtension.class)
class BoardMemberServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private BoardMemberRepository boardMemberRepository;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private BoardMemberMapper boardMemberMapper;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private BoardValidator boardValidator;

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @Mock
    private AppAccessValidator appAccessValidator;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private BoardMemberServiceImpl boardMemberServiceImpl;

    @Nested
    class AddMember {

        @Test
        void softDeletedMemberExists_restoresInsteadOfCreatingNew() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();
            UUID restoredMemberId = UUID.randomUUID();

            User requestingUser = User.builder().build();
            requestingUser.setId(UUID.randomUUID());

            User targetUser = User.builder().build();
            targetUser.setId(targetUserId);

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            BoardMember deletedMember = BoardMember.builder()
                    .board(board)
                    .user(targetUser)
                    .role(BoardMemberRole.MEMBER)
                    .build();
            deletedMember.setId(restoredMemberId);

            BoardMember restoredMember = BoardMember.builder()
                    .board(board)
                    .user(targetUser)
                    .role(BoardMemberRole.MEMBER)
                    .build();
            restoredMember.setId(restoredMemberId);

            BoardMemberAddRequest request = new BoardMemberAddRequest(targetUserId, BoardMemberRole.MEMBER);

            BoardMemberResponse expectedResponse = new BoardMemberResponse(restoredMemberId, null, null, null, null, BoardMemberRole.MEMBER, null, true);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, requestingUser.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(boardMemberRepository.existsByBoard_IdAndUser_Id(boardId, targetUserId)).thenReturn(false);
            when(boardMemberRepository.findDeletedByBoardIdAndUserId(boardId, targetUserId))
                    .thenReturn(Optional.of(deletedMember));
            doNothing().when(boardMemberRepository).restoreById(restoredMemberId);
            when(boardMemberRepository.findById(restoredMemberId)).thenReturn(Optional.of(restoredMember));
            when(boardMemberMapper.toResponse(restoredMember, true)).thenReturn(expectedResponse);

            // Act
            BoardMemberResponse result = boardMemberServiceImpl.addMember(requestingUser, organizationId, boardId, request);

            // Assert
            assertNotNull(result);
            verify(boardMemberRepository).restoreById(restoredMemberId);
            verify(boardMemberRepository).findById(restoredMemberId);
            verify(boardMemberRepository, never()).save(any(BoardMember.class));
            verify(userQueryService, never()).getUserEntityById(any());
        }

        @Test
        void noDeletedMember_createsNewMember() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();

            User requestingUser = User.builder().build();
            requestingUser.setId(UUID.randomUUID());

            User targetUser = User.builder().build();
            targetUser.setId(targetUserId);

            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);

            BoardMember newMember = BoardMember.builder()
                    .board(board)
                    .user(targetUser)
                    .role(BoardMemberRole.MEMBER)
                    .build();
            newMember.setId(UUID.randomUUID());

            BoardMemberAddRequest request = new BoardMemberAddRequest(targetUserId, BoardMemberRole.MEMBER);

            BoardMemberResponse expectedResponse = new BoardMemberResponse(newMember.getId(), null, null, null, null, BoardMemberRole.MEMBER, null, true);

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, requestingUser.getId(), BoardMemberRole.ADMIN)).thenReturn(board);
            when(boardMemberRepository.existsByBoard_IdAndUser_Id(boardId, targetUserId)).thenReturn(false);
            when(boardMemberRepository.findDeletedByBoardIdAndUserId(boardId, targetUserId))
                    .thenReturn(Optional.empty());
            when(userQueryService.getUserEntityById(targetUserId)).thenReturn(targetUser);
            when(boardMemberMapper.toEntity(request)).thenReturn(newMember);
            when(boardMemberRepository.save(newMember)).thenReturn(newMember);
            when(boardMemberMapper.toResponse(newMember, true)).thenReturn(expectedResponse);

            // Act
            BoardMemberResponse result = boardMemberServiceImpl.addMember(requestingUser, organizationId, boardId, request);

            // Assert
            assertNotNull(result);
            verify(boardMemberRepository).save(newMember);
            verify(boardMemberRepository, never()).restoreById(any());
        }
    
        @Test
        void userNotActiveInBoardOrganization_throwsNotInOrganizationAndAddsNothing() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();
            User requestingUser = User.builder().build();
            requestingUser.setId(UUID.randomUUID());
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            BoardMemberAddRequest request = new BoardMemberAddRequest(targetUserId, BoardMemberRole.MEMBER);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, requestingUser.getId(),
                    BoardMemberRole.ADMIN)).thenReturn(board);
            doThrow(new ConflictException(ExceptionDomainKaizenError.STA_BOARD_MEMBER_NOT_IN_ORGANIZATION))
                    .when(boardValidator).ensureUserIsOrganizationMember(organizationId, targetUserId);

            // Act & Assert
            assertThatThrownBy(() -> boardMemberServiceImpl.addMember(requestingUser, organizationId, boardId, request))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("KAI_STA_019"));
            verify(boardMemberRepository, never()).save(any(BoardMember.class));
            verify(boardMemberRepository, never()).restoreById(any());
            verify(boardMemberRepository, never()).existsByBoard_IdAndUser_Id(any(), any());
        }

        @Test
        void softDeletedMemberWhoLeftOrganization_isNotRestored() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();
            User requestingUser = User.builder().build();
            requestingUser.setId(UUID.randomUUID());
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            BoardMemberAddRequest request = new BoardMemberAddRequest(targetUserId, BoardMemberRole.MEMBER);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, requestingUser.getId(),
                    BoardMemberRole.ADMIN)).thenReturn(board);
            doThrow(new ConflictException(ExceptionDomainKaizenError.STA_BOARD_MEMBER_NOT_IN_ORGANIZATION))
                    .when(boardValidator).ensureUserIsOrganizationMember(organizationId, targetUserId);

            // Act & Assert
            assertThrows(ConflictException.class,
                    () -> boardMemberServiceImpl.addMember(requestingUser, organizationId, boardId, request));
            verify(boardMemberRepository, never()).findDeletedByBoardIdAndUserId(any(), any());
            verify(boardMemberRepository, never()).restoreById(any());
        }

        @Test
        void userWithoutKaizen_throwsMemberWithoutAppAccessAndAddsNothing() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();
            User requestingUser = User.builder().build();
            requestingUser.setId(UUID.randomUUID());
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            BoardMemberAddRequest request = new BoardMemberAddRequest(targetUserId, BoardMemberRole.MEMBER);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, requestingUser.getId(),
                    BoardMemberRole.ADMIN)).thenReturn(board);
            doThrow(new ValidationException(ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS))
                    .when(appAccessValidator).ensureUsersHaveAppAccess(organizationId, App.KAIZEN, List.of(targetUserId));

            // Act & Assert
            assertThatThrownBy(() -> boardMemberServiceImpl.addMember(requestingUser, organizationId, boardId, request))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("APP_VAL_002"));
            verify(boardMemberRepository, never()).existsByBoard_IdAndUser_Id(any(), any());
            verify(boardMemberRepository, never()).restoreById(any());
            verify(boardMemberRepository, never()).save(any(BoardMember.class));
        }
    }

    @Nested
    class UpdateMemberRole {

        @Test
        void memberWithoutKaizen_updatesRoleAndReportsKaizenAccessFalse() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID targetUserId = UUID.randomUUID();
            User requestingUser = User.builder().build();
            requestingUser.setId(UUID.randomUUID());
            User targetUser = User.builder().build();
            targetUser.setId(targetUserId);
            Board board = Board.builder().organizationId(organizationId).name("Board").prefix("B").build();
            board.setId(boardId);
            BoardMember member = BoardMember.builder().board(board).user(targetUser)
                    .role(BoardMemberRole.MEMBER).build();
            BoardMemberResponse expectedResponse = new BoardMemberResponse(
                    UUID.randomUUID(), targetUserId, null, null, null, BoardMemberRole.ADMIN, null, false);
            when(boardAccessService.verifyBoardAccess(organizationId, boardId, requestingUser.getId(),
                    BoardMemberRole.ADMIN)).thenReturn(board);
            when(boardValidator.ensureBoardMemberExists(boardId, targetUserId)).thenReturn(member);
            when(boardMemberRepository.save(member)).thenReturn(member);
            when(appAccessQueryService.hasAppAccess(organizationId, targetUserId, App.KAIZEN)).thenReturn(false);
            when(boardMemberMapper.toResponse(member, false)).thenReturn(expectedResponse);

            // Act
            BoardMemberResponse result = boardMemberServiceImpl.updateMemberRole(requestingUser, organizationId,
                    boardId, targetUserId, new BoardMemberUpdateRequest(BoardMemberRole.ADMIN));

            // Assert
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(member.getRole()).isEqualTo(BoardMemberRole.ADMIN);
        }
    }
}
