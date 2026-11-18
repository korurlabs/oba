package com.korur.oba.apps.kaya.link.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.link.enums.LinkStatus;

/**
 * One link of a Markdown file to a repository-relative path, built by
 * {@link com.korur.oba.apps.kaya.link.mapper.LinkMapper} from a
 * {@link com.korur.oba.apps.kaya.render.helper.LinkCandidate}.
 */
@Schema(description = "A link from one delivered Markdown file to a path Kaya delivers")
public record LinkResponse(
        @Schema(description = "The delivered file that holds the link", example = ".agents/rules/11-backend-rules.md")
        String sourcePath,

        @Schema(description = "The repository-relative target; a directory ends with a slash",
                example = "docs/private/conventions/backend/32-soft-delete-convention.md")
        String targetPath,

        @Schema(description = "The 1-based line of the link in the source", example = "12")
        int line,

        @Schema(description = "FOUND when the scope delivers the target, or for a directory a file below it; BROKEN otherwise",
                example = "FOUND")
        LinkStatus status
) {
}
