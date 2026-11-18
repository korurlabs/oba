package com.korur.oba.apps.kazan.alertroute.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.alertroute.constant.AlertRouteConstant;

/**
 * Request payload used to map a system to the board that receives its alerts.
 *
 * The server does not normalise the system key: the client trims and lowercases
 * it, and any other value is refused, so both sides check the one pattern that the
 * column's check constraint repeats.
 */
@Schema(description = "Alert route create request")
public record AlertRouteCreateRequest(

        // Value of the alert's system label, fixed after create.
        @Schema(description = "System that sends the alerts, the value of the alert's system label",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "fabrikam")
        @NotBlank(message = AlertRouteConstant.SYSTEM_KEY_REQUIRED_MESSAGE)
        @Size(max = AlertRouteConstant.SYSTEM_KEY_MAX_LENGTH, message = AlertRouteConstant.SYSTEM_KEY_SIZE_MESSAGE)
        @Pattern(regexp = AlertRouteConstant.SYSTEM_KEY_PATTERN, message = AlertRouteConstant.SYSTEM_KEY_PATTERN_MESSAGE)
        String systemKey,

        // Board that receives the system's incident tickets.
        @Schema(description = "Board that receives the incidents", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a")
        @NotNull(message = AlertRouteConstant.BOARD_ID_REQUIRED_MESSAGE)
        UUID boardId,

        // Whether this route also receives the alerts of unmapped systems; null means false.
        @Schema(description = "Default board for unmapped systems; null means false", nullable = true,
                example = "false")
        Boolean isDefault
) {
}
