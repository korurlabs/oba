package com.korur.oba.apps.kaya.render.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.AgentTool;

/**
 * What one AI tool reads in a repository Kaya delivers, and what it reads that Kaya does not deliver.
 */
@Schema(description = "Every path one AI tool reads, with its state in the scope, and the paths Kaya does not deliver")
public record ToolMappingResponse(
        @Schema(description = "The tool", example = "CLAUDE_CODE")
        AgentTool tool,

        @Schema(description = "Display name of the tool", example = "Claude Code")
        String name,

        @Schema(description = "How the tool finds the sources under .agents/", example = "Reads AGENTS.md and its own .claude folder")
        String summary,

        @Schema(description = "The paths the tool reads, in the order it loads them")
        List<ToolPathResponse> reads,

        @Schema(description = "Paths the tool reads that Kaya does not deliver")
        List<ToolGapResponse> notDelivered
) {
}
