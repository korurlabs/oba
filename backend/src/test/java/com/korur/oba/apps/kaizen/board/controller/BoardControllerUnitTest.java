package com.korur.oba.apps.kaizen.board.controller;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.constant.controller.BoardControllerResponseConstant;
import com.korur.oba.apps.kaizen.board.dto.response.BoardListResponse;
import com.korur.oba.apps.kaizen.board.service.BoardQueryService;
import com.korur.oba.apps.kaizen.board.service.BoardService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class BoardControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private BoardService boardService;

    @Mock
    private BoardQueryService boardQueryService;

    private BoardController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new BoardController(boardService, boardQueryService, new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void getBoardBySlug_memberBoard_returnsBoardWith200() {
        // Arrange
        BoardListResponse board = mock(BoardListResponse.class);
        when(boardQueryService.getBoardBySlug(user, organizationId, "my-board")).thenReturn(board);

        // Act
        ResponseEntity<CustomResponse<BoardListResponse>> response = controller.getBoardBySlug(user, organizationId, "my-board");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo(BoardControllerResponseConstant.RESPONSE_BOARD_RETRIEVED);
        assertThat(response.getBody().data()).isSameAs(board);
    }

    @Test
    void getBoardBySlug_boardNotVisible_propagatesNotFound() {
        // Arrange
        when(boardQueryService.getBoardBySlug(user, organizationId, "secret"))
                .thenThrow(new NotFoundException(ExceptionDomainKaizenError.NOT_BOARD_FOUND));

        // Act and assert
        assertThatThrownBy(() -> controller.getBoardBySlug(user, organizationId, "secret"))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ExceptionDomainKaizenError.NOT_BOARD_FOUND.getCode());
    }

    @Test
    void getBoard_memberBoard_returnsBoardWith200() {
        // Arrange
        UUID boardId = UUID.randomUUID();
        BoardListResponse board = mock(BoardListResponse.class);
        when(boardQueryService.getBoard(user, organizationId, boardId)).thenReturn(board);

        // Act
        ResponseEntity<CustomResponse<BoardListResponse>> response = controller.getBoard(user, organizationId, boardId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(BoardControllerResponseConstant.RESPONSE_BOARD_RETRIEVED);
        assertThat(response.getBody().data()).isSameAs(board);
    }

    @Test
    void getBoard_boardNotVisible_propagatesNotFound() {
        // Arrange
        UUID boardId = UUID.randomUUID();
        when(boardQueryService.getBoard(user, organizationId, boardId))
                .thenThrow(new NotFoundException(ExceptionDomainKaizenError.NOT_BOARD_FOUND));

        // Act and assert
        assertThatThrownBy(() -> controller.getBoard(user, organizationId, boardId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ExceptionDomainKaizenError.NOT_BOARD_FOUND.getCode());
    }
}
