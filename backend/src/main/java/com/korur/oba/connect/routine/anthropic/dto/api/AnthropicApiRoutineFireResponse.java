package com.korur.oba.connect.routine.anthropic.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK

// 4. Internal

/**
 * Anthropic's answer to a fire that started a session.
 *
 * @param type                 the kind of answer
 * @param claudeCodeSessionId  the id of the session the fire started
 * @param claudeCodeSessionUrl the link to that session in claude.ai
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicApiRoutineFireResponse(
        @JsonProperty("type") String type,
        @JsonProperty("claude_code_session_id") String claudeCodeSessionId,
        @JsonProperty("claude_code_session_url") String claudeCodeSessionUrl
) {
}
