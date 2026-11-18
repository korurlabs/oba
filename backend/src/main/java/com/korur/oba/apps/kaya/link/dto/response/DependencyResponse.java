package com.korur.oba.apps.kaya.link.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.link.dto.response.embedded.DependencyEmbeddedResponse;
import com.korur.oba.apps.kaya.render.enums.EntityKind;

/**
 * What one delivered file links to and what uses it within a scope, so a member sees what a change
 * to it affects before saving.
 */
@Schema(description = "The dependencies of one file of a scope")
public record DependencyResponse(
        @Schema(description = "Path relative to the repository root", example = ".agents/rules/11-backend-rules.md")
        String path,

        @Schema(description = "Kind of entity stored at the path; null for the link of a skill", nullable = true,
                example = "RULE")
        EntityKind kind,

        @Schema(description = "What the file depends on: its Markdown links, a rule's conventions, a skill's parts; "
                + "by relation and path")
        List<DependencyEmbeddedResponse> linksTo,

        @Schema(description = "What depends on the file: Markdown links to it, rules citing it, skills sharing it; "
                + "by relation and path")
        List<DependencyEmbeddedResponse> usedBy
) {
}
