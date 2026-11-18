package com.korur.oba.apps.kaizen.label.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.helper.BoardHelper;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.label.dto.response.LabelResponse;
import com.korur.oba.apps.kaizen.label.entity.Label;
import com.korur.oba.apps.kaizen.label.mapper.LabelMapper;
import com.korur.oba.apps.kaizen.label.repository.LabelRepository;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class LabelQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private LabelMapper labelMapper;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private BoardHelper boardHelper;

    @InjectMocks
    private LabelQueryServiceImpl labelQueryServiceImpl;

    @Nested
    class ListLabels {

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
                    labelQueryServiceImpl.listLabels(user, organizationId, boardId));
        }

        @Test
        void userIsBoardMember_returnsLabelList() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID labelId = UUID.randomUUID();

            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            Board board = Board.builder().name("Board").prefix("B").build();
            board.setId(boardId);

            Label label = Label.builder().name("Bug").color("#ff0000").build();
            label.setId(labelId);

            LabelResponse expectedResponse = new LabelResponse(labelId, "Bug", "#ff0000", boardId, "Board");

            when(boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER)).thenReturn(board);
            when(boardHelper.resolveRelatedBoardEntities(board, user.getId())).thenReturn(Collections.emptyList());
            when(labelRepository.findByBoard_IdIn(anyList())).thenReturn(List.of(label));
            when(labelMapper.toResponse(label)).thenReturn(expectedResponse);

            // Act
            List<LabelResponse> result = labelQueryServiceImpl.listLabels(user, organizationId, boardId);

            // Assert
            assertNotNull(result);
            verify(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
            verify(boardHelper).resolveRelatedBoardEntities(board, user.getId());
            verify(labelRepository).findByBoard_IdIn(anyList());
        }
    }
}
