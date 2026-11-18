package com.korur.oba.apps.kaizen.sprint.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request payload for unlinking a board from a sprint")
public record SprintUnlinkRequest(
    
    @Schema(description = "Sprint transfer", nullable = true)
    SprintUnlinkTransferRequest transfer,
    
    @Schema(description = "Delete tickets", nullable = true)
    Boolean deleteTickets
) {

}
