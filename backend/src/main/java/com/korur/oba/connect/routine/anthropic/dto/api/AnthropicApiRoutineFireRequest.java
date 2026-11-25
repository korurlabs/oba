package com.korur.oba.connect.routine.anthropic.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK

// 4. Internal

/**
 * Body of POST /v1/claude_code/routines/{routineId}/fire. Anthropic hands the text to the session
 * wrapped as an untrusted payload.
 *
 * @param text the text the session receives
 */
public record AnthropicApiRoutineFireRequest(
        @JsonProperty("text") String text
) {
}
