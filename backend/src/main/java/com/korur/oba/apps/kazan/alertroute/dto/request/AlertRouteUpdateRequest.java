package com.korur.oba.apps.kazan.alertroute.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.alertroute.constant.AlertRouteConstant;

/**
 * Request payload used to change the board and the default flag of an alert route.
 * The system key is not part of it: it is fixed after create.
 */
@Schema(description = "Alert route update request")
public record AlertRouteUpdateRequest(

        // Board that receives the system's incident tickets.
        @Schema(description = "Board that receives the incidents", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a")
        @NotNull(message = AlertRouteConstant.BOARD_ID_REQUIRED_MESSAGE)
        UUID boardId,

        // Whether this route also receives the alerts of unmapped systems.
        @Schema(description = "Default board for unmapped systems", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "true")
        @NotNull(message = AlertRouteConstant.IS_DEFAULT_REQUIRED_MESSAGE)
        Boolean isDefault
) {
}
