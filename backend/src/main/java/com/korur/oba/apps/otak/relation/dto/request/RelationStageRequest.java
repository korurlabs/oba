package com.korur.oba.apps.otak.relation.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;

/**
 * Request payload used to move a lead to another stage; CLOSED makes it a customer.
 */
@Schema(description = "Relation stage change request")
public record RelationStageRequest(

        @Schema(description = "New stage of the lead: a lead stage, or CLOSED to make it a customer; never DIRECT",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "WARM")
        @NotNull(message = RelationConstant.STAGE_REQUIRED_MESSAGE)
        PipelineStage stage
) {
}
