package com.korur.oba.apps.kazan.ai.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal

/**
 * Request payload for AI-powered ticket analysis.
 */
@Schema(description = "AI analyze request")
public record AiAnalyzeRequest(

        // Free-text description of the work to analyze.
        @Schema(description = "User input describing the work", nullable = false)
        @NotBlank(message = "User input is required")
        @Size(min = 3, max = 5000, message = "Input must be between 3 and 5000 characters")
        String userInput
) {
}
