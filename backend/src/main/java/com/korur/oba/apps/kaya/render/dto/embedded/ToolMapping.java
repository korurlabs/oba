package com.korur.oba.apps.kaya.render.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.AgentTool;

/**
 * Every path one AI tool reads, each with what the scope delivers of it.
 *
 * @param tool  the tool
 * @param reads one {@link ToolPathCount} per path, in the order of
 *              {@link com.korur.oba.apps.kaya.render.helper.ToolPaths#pathsOf}
 */
public record ToolMapping(
        AgentTool tool,
        List<ToolPathCount> reads
) {
}
