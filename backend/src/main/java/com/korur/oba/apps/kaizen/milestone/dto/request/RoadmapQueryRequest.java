package com.korur.oba.apps.kaizen.milestone.dto.request;

// 1. Framework-level
import org.springframework.format.annotation.DateTimeFormat;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.time.LocalDate;

// 4. Internal

/**
 * Query parameters for the roadmap of a board.
 */
@Getter
@Setter
@NoArgsConstructor
public class RoadmapQueryRequest {

    /**
     * Add one lane per direct child board the user is a member of.
     */
    @Schema(description = "Add one lane per direct child board the user is a member of", example = "false")
    private boolean includeChildBoards;

    /**
     * Window start. Keeps milestones whose target date is on or after it.
     */
    @Schema(description = "Window start (YYYY-MM-DD)", nullable = true, example = "2026-10-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    /**
     * Window end. Keeps milestones whose start (or target, without a start) is on or before it.
     */
    @Schema(description = "Window end (YYYY-MM-DD)", nullable = true, example = "2026-12-31")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
}
