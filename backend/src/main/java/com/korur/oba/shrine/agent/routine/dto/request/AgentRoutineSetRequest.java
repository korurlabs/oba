package com.korur.oba.shrine.agent.routine.dto.request;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload to set the routine of a ROUTINE agent. For Anthropic the id is the routine's
 * trig_ id and the secret is its fire token.
 */
@Schema(description = "Agent routine set request")
public record AgentRoutineSetRequest(

        @Schema(description = "Id of the hosted routine, in the format of the agent's provider",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "trig_01AbCdEf")
        @NotBlank(message = "Routine id is required")
        @Size(max = 128, message = "Routine id must not exceed 128 characters")
        String routineId,

        @Schema(description = "Secret that fires the routine; stored encrypted and never returned",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "sk-ant-oat01-AbCdEf")
        @NotBlank(message = "Routine secret is required")
        @Size(max = 512, message = "Routine secret must not exceed 512 characters")
        String routineSecret
) {

    /**
     * Leaves the secret out, so a request that ends up in a log or an error message does not carry it.
     */
    @Override
    public String toString() {
        return "AgentRoutineSetRequest[routineId=" + routineId + ", routineSecret=***]";
    }
}
