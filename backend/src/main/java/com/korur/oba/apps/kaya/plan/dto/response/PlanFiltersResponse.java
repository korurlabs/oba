package com.korur.oba.apps.kaya.plan.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.plan.dto.response.embedded.PlanReferenceResponse;

@Schema(description = "The options of the plans list filters: the values the organization's plans hold")
public record PlanFiltersResponse(
        @Schema(description = "Repositories that have plans, by name")
        List<PlanReferenceResponse> repositories,

        @Schema(description = "Packs that have plans, by name")
        List<PlanReferenceResponse> packs,

        @Schema(description = "Users of latest plan versions, by name")
        List<PlanReferenceResponse> users
) {
}
