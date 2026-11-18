package com.korur.oba.apps.kazan.sprint.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * Request payload for starting a sprint. The body is optional; a missing body
 * starts the sprint only when no linked board has another ACTIVE sprint.
 */
@Schema(description = "Sprint start request")
public record SprintStartRequest(

        // True closes the other ACTIVE sprints on the linked boards and moves their
        // unfinished tickets into this sprint before it starts. Null or false answers
        // 409 when such a sprint exists.
        @Schema(description = "Close the active sprints on the linked boards and move their unfinished "
                + "tickets into this sprint, then start it", nullable = true)
        Boolean takeOver
) {
}
