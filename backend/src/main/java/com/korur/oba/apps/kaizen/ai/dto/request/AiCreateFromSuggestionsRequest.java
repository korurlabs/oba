package com.korur.oba.apps.kaizen.ai.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.List;

import com.korur.oba.apps.kaizen.ai.dto.response.AiTicketSuggestionResponse;

/**
 * Request payload for creating tickets from AI-generated suggestions.
 */
@Schema(description = "AI create from suggestions request")
public record AiCreateFromSuggestionsRequest(

        // List of AI-generated suggestions to create as tickets.
        @Schema(description = "Suggestions to create", nullable = false)
        @NotEmpty(message = "At least one suggestion is required")
        List<AiTicketSuggestionResponse> suggestions,

        // Whether to add created tickets to the board backlog.
        @Schema(description = "Add tickets to backlog", nullable = false)
        @NotNull(message = "addToBacklog flag is required")
        Boolean addToBacklog
) {
}
