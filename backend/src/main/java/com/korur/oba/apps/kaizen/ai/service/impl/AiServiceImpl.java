package com.korur.oba.apps.kaizen.ai.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.ai.constant.AiConstant;
import com.korur.oba.apps.kaizen.ai.constant.service.AiServiceLogConstant;
import com.korur.oba.apps.kaizen.ai.dto.request.AiAnalyzeRequest;
import com.korur.oba.apps.kaizen.ai.dto.request.AiCreateFromSuggestionsRequest;
import com.korur.oba.apps.kaizen.ai.dto.response.AiAnalysisResponse;
import com.korur.oba.apps.kaizen.ai.dto.response.AiTicketSuggestionResponse;
import com.korur.oba.apps.kaizen.ai.service.AiService;
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.backlog.service.BacklogQueryService;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.repository.BoardRepository;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.ticket.dto.response.TicketDetailResponse;
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.apps.kaizen.ticket.service.TicketService;
import com.korur.oba.connect.intelligence.core.enums.IntelligenceProvider;
import com.korur.oba.connect.intelligence.core.enums.IntelligenceUseCase;
import com.korur.oba.connect.intelligence.core.service.IntelligenceService;
import com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implements AI-powered ticket analysis and batch creation via the intelligence
 * layer.
 */
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AiServiceImpl.class);

    private final IntelligenceService intelligenceService;
    private final BoardRepository boardRepository;
    private final BoardAccessService boardAccessService;
    private final TicketService ticketService;
    private final BacklogQueryService backlogQueryService;
    private final ObjectMapper objectMapper;
    private final LoggingService loggingService;

    /**
     * Checks if AI features are available.
     * Validates that the requesting user has at least VIEWER access to the board.
     */
    @Override
    public boolean isAvailable(User user, UUID organizationId, UUID boardId) {
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
        return intelligenceService.isAvailable(IntelligenceProvider.ANTHROPIC);
    }

    /**
     * Analyzes user input and generates ticket suggestions.
     */
    @Override
    public AiAnalysisResponse analyzeTicket(User user, UUID organizationId, UUID boardId, AiAnalyzeRequest request) {
        // Validate the user can contribute to this board and build context.
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Verify AI features are available before making the API call.
        if (!intelligenceService.isAvailable(IntelligenceProvider.ANTHROPIC)) {
            throw new IntegrationErrorException(
                    ExceptionDomainKaizenError.UPS_AI_UNAVAILABLE,
                    Map.of(AiConstant.FIELD_BOARD_ID, boardId));
        }

        // Log the analysis start for observability.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.SERVICE,
                AiServiceLogConstant.AI_ANALYZE_STARTED,
                boardId);

        // Build the user message with board context.
        String contextInfo = String.format(
                "\nBoard Context: \"%s\"%s",
                board.getName(),
                board.getDescription() != null ? " - " + board.getDescription() : "");
        String userMessage = contextInfo + "\n\nUser Request:\n" + request.userInput();

        // Call the intelligence layer via use-case routing.
        String responseText = intelligenceService.chat(IntelligenceUseCase.TICKET_ANALYSIS, AiConstant.SYSTEM_PROMPT,
                userMessage);

        // Parse the JSON response into the analysis result.
        AiAnalysisResponse response = parseAnalysisResponse(responseText);

        // Log the analysis completion.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.SERVICE,
                AiServiceLogConstant.AI_ANALYZE_COMPLETED,
                boardId);

        return response;
    }

    /**
     * Creates tickets from AI-generated suggestions in a single transaction.
     */
    @Override
    @Transactional
    public List<TicketDetailResponse> createFromSuggestions(User user, UUID organizationId, UUID boardId,
            AiCreateFromSuggestionsRequest request) {
        // Validate the user can contribute to this board.
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.MEMBER);

        // Log the batch creation start.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.SERVICE,
                AiServiceLogConstant.AI_CREATE_FROM_SUGGESTIONS,
                request.suggestions().size(),
                boardId);

        // Resolve the backlog if tickets should be added there.
        Backlog backlog = null;
        if (Boolean.TRUE.equals(request.addToBacklog())) {
            backlog = backlogQueryService.findBacklogEntityByBoardId(boardId).orElse(null);
        }

        List<TicketDetailResponse> createdTickets = new ArrayList<>();

        // Create each suggested ticket via the ticket service.
        for (AiTicketSuggestionResponse suggestion : request.suggestions()) {
            long nextNumber = boardRepository.incrementAndGetTicketCounter(boardId);
            String key = board.getPrefix() + "-" + nextNumber;

            TicketDetailResponse ticketResponse = ticketService.createTicketDirect(
                    user,
                    board,
                    backlog,
                    suggestion.title(),
                    suggestion.description(),
                    key,
                    TicketType.valueOf(suggestion.type()),
                    TicketPriority.valueOf(suggestion.priority()));
            createdTickets.add(ticketResponse);
        }

        // Log the batch creation completion.
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.SERVICE,
                AiServiceLogConstant.AI_CREATE_FROM_SUGGESTIONS_COMPLETED,
                createdTickets.size(),
                boardId);

        return createdTickets;
    }

    /**
     * Parses the Claude API text response into a structured analysis result.
     */
    private AiAnalysisResponse parseAnalysisResponse(String responseText) {
        try {
            return objectMapper.readValue(responseText, AiAnalysisResponse.class);
        } catch (Exception e) {
            throw new InternalServerErrorException(
                    ExceptionDomainKaizenError.UPS_AI_ANALYSIS_FAILED,
                    Map.of("reason", "Failed to parse AI analysis response"));
        }
    }
}
