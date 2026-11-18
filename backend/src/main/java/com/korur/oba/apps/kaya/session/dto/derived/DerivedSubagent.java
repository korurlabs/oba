package com.korur.oba.apps.kaya.session.dto.derived;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;

// 4. Internal

/**
 * A subagent of a session as its events describe it.
 *
 * @param agentId      the id Claude Code gave the subagent
 * @param subagentType the agent file it runs
 * @param model        the model it ran on, or null when no event named one
 * @param description  the short description of its Agent call, or null
 * @param startedAt    when its Agent call or its start was recorded
 * @param endedAt      when it stopped, or null while it runs
 */
public record DerivedSubagent(
        String agentId,
        String subagentType,
        String model,
        String description,
        Instant startedAt,
        Instant endedAt
) {
}
