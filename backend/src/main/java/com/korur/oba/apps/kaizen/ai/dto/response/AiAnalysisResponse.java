package com.korur.oba.apps.kaizen.ai.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * Response payload for AI ticket analysis containing suggestions and reasoning.
 */
@Schema(description = "AI analysis response")
public record AiAnalysisResponse(

        // List of AI-generated ticket suggestions.
        @Schema(description = "Ticket suggestions", nullable = false)
        List<AiTicketSuggestionResponse> suggestions,

        // AI reasoning explaining the analysis.
        @Schema(description = "AI reasoning", nullable = false)
        String reasoning,

        // Whether the AI recommends splitting the request into multiple tickets.
        @Schema(description = "Should split into multiple tickets", nullable = false)
        boolean shouldSplit
) {
}
