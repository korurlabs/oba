package com.korur.oba.connect.routine.anthropic.properties;

// 1. Framework-level

// 2. Third-party
import lombok.Getter;
import lombok.Setter;

// 3. Java SDK
import java.time.Duration;

// 4. Internal
import com.korur.oba.connect.routine.anthropic.constant.AnthropicRoutineConstant;

/**
 * Configuration of Anthropic routine fires, bound from the prefix "oba.integrations.routine.anthropic".
 *
 * There is no application-wide key: each routine carries its own fire secret, which the caller
 * passes. A routine fires only when an agent has one saved, so no switch is kept here.
 */
@Getter
@Setter
public class AnthropicRoutineProperties {

    private String baseUrl = AnthropicRoutineConstant.DEFAULT_BASE_URL;

    // Value of the anthropic-beta header the fire endpoint requires.
    private String betaHeader = AnthropicRoutineConstant.DEFAULT_BETA_HEADER;

    // Value of the anthropic-version header.
    private String version = AnthropicRoutineConstant.DEFAULT_VERSION;

    private Duration connectTimeout = AnthropicRoutineConstant.DEFAULT_CONNECT_TIMEOUT;

    private Duration readTimeout = AnthropicRoutineConstant.DEFAULT_READ_TIMEOUT;
}
