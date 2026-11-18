package com.korur.oba.apps.kazan.ai.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

import com.korur.oba.apps.kazan.ai.dto.request.AiAnalyzeRequest;
import com.korur.oba.apps.kazan.ai.dto.request.AiCreateFromSuggestionsRequest;
import com.korur.oba.apps.kazan.ai.dto.response.AiAnalysisResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketDetailResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * AI-powered ticket analysis and creation operations.
 */
public interface AiService {

    /**
     * Checks if AI features are available (API key configured).
     * Validates that the requesting user has at least VIEWER access to the board.
     */
    boolean isAvailable(User user, UUID organizationId, UUID boardId);

    /**
     * Analyzes user input and generates ticket suggestions using Claude AI.
     */
    AiAnalysisResponse analyzeTicket(User user, UUID organizationId, UUID boardId, AiAnalyzeRequest request);

    /**
     * Creates tickets from AI-generated suggestions.
     */
    List<TicketDetailResponse> createFromSuggestions(
            User user, UUID organizationId, UUID boardId, AiCreateFromSuggestionsRequest request);
}
