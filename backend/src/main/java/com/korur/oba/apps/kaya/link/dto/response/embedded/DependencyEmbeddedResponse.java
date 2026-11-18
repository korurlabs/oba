package com.korur.oba.apps.kaya.link.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.link.enums.DependencyRelation;
import com.korur.oba.apps.kaya.link.enums.LinkStatus;
import com.korur.oba.apps.kaya.render.enums.EntityKind;

/**
 * One file a file links to or is used by, with the reason and whether the scope delivers it.
 */
@Schema(description = "A file on the other end of a dependency")
public record DependencyEmbeddedResponse(
        @Schema(description = "Path relative to the repository root; a directory ends with a slash",
                example = "docs/private/conventions/backend/logging.md")
        String path,

        @Schema(description = "Kind of entity stored at the path; null for a directory or a path no kind takes",
                nullable = true, example = "CONVENTION")
        EntityKind kind,

        @Schema(description = "MARKDOWN_LINK, RULE_CONVENTION or SKILL_PART", example = "RULE_CONVENTION")
        DependencyRelation relation,

        @Schema(description = "FOUND when the scope delivers the path, or for a directory a file below it; BROKEN otherwise",
                example = "FOUND")
        LinkStatus status,

        @Schema(description = "The version of the scope that delivers the path; null when BROKEN or for a directory",
                nullable = true, example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID versionId
) {
}
