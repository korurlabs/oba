package com.korur.oba.apps.kaizen.ai.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.fasterxml.jackson.databind.ObjectMapper;
import com.korur.oba.apps.kaizen.ai.dto.request.AiAnalyzeRequest;
import com.korur.oba.apps.kaizen.ai.dto.request.AiCreateFromSuggestionsRequest;
import com.korur.oba.apps.kaizen.ai.dto.response.AiTicketSuggestionResponse;
import com.korur.oba.apps.kaizen.backlog.service.BacklogQueryService;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.repository.BoardRepository;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.ticket.service.TicketService;
import com.korur.oba.connect.intelligence.core.service.IntelligenceService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class AiServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private IntelligenceService intelligenceService;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardAccessService boardAccessService;

    @Mock
    private TicketService ticketService;

    @Mock
    private BacklogQueryService backlogQueryService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private AiServiceImpl aiServiceImpl;

    @Nested
    class AnalyzeTicketAuthorization {

        @Test
        void viewerMember_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            AiAnalyzeRequest request = new AiAnalyzeRequest("Implement user login with JWT");

            doThrow(new ForbiddenException(
                    ExceptionDomainKaizenError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of("boardId", boardId)))
                    .when(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    aiServiceImpl.analyzeTicket(user, organizationId, boardId, request));
            verifyNoInteractions(intelligenceService);
        }
    }

    @Nested
    class CreateFromSuggestionsAuthorization {

        @Test
        void viewerMember_throwsForbiddenException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            User user = User.builder().build();
            user.setId(UUID.randomUUID());

            AiTicketSuggestionResponse suggestion = new AiTicketSuggestionResponse(
                    "Fix login bug", "Fix the login bug", "FEATURE", "HIGH");
            AiCreateFromSuggestionsRequest request = new AiCreateFromSuggestionsRequest(
                    List.of(suggestion), false);

            doThrow(new ForbiddenException(
                    ExceptionDomainKaizenError.SEC_INSUFFICIENT_BOARD_PERMISSIONS,
                    Map.of("boardId", boardId)))
                    .when(boardAccessService).verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

            // Act & Assert
            assertThrows(ForbiddenException.class, () ->
                    aiServiceImpl.createFromSuggestions(user, organizationId, boardId, request));
            verify(ticketService, never()).createTicketDirect(any(), any(), any(), any(), any(), any(), any(), any());
        }
    }
}
