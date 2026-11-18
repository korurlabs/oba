package com.korur.oba.apps.kazan.sprint.dto.request;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Sprint unlink transfer request")
public record SprintUnlinkTransferRequest(
    @Schema(description = "Board id", nullable = false)
    @NotNull
    UUID boardId,

    @Schema(description = "Sprint id", nullable = true) 
    UUID sprintId,

    @Schema(description = "Backlog id", nullable = true)
    UUID backlogId
) {

}
