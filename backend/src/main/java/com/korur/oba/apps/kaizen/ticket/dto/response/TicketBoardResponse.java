package com.korur.oba.apps.kaizen.ticket.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ticket board")
public record TicketBoardResponse(
        @Schema(description = "Board id", nullable = false)
        UUID id,

        @Schema(description = "Board name", nullable = false)
        String name,

        @Schema(description = "Board prefix", nullable = false)
        String prefix,

        @Schema(description = "URL slug of the board, derived from its name and unique among live boards",
                example = "fabrikam", nullable = false)
        String slug,

        @Schema(description = "Board icon", nullable = true)
        String icon,

        @Schema(description = "Board color", nullable = true) 
        String color
    ) {

}
