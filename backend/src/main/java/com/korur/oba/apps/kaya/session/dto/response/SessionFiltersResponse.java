package com.korur.oba.apps.kaya.session.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.session.dto.response.embedded.SessionFilterMachineResponse;
import com.korur.oba.apps.kaya.session.dto.response.embedded.SessionFilterSubscriptionResponse;
import com.korur.oba.apps.kaya.session.dto.response.embedded.SessionFilterUserResponse;

@Schema(description = "The options of the wall's user, machine, model and subscription filters: the values the "
        + "organization's sessions hold")
public record SessionFiltersResponse(
        @Schema(description = "Users with sessions, ordered by display name")
        List<SessionFilterUserResponse> users,

        @Schema(description = "Machines with sessions, ordered by machine name")
        List<SessionFilterMachineResponse> machines,

        @Schema(description = "Models of the sessions, ordered")
        List<String> models,

        @Schema(description = "Claude subscriptions of the sessions, ordered by name")
        List<SessionFilterSubscriptionResponse> subscriptions
) {
}
