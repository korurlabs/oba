package com.korur.oba.apps.kaya.rule.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * A convention path a rule's body links to.
 */
@Schema(description = "A convention the rule's body links to")
public record RuleConventionEmbeddedResponse(
        @Schema(description = "Path of the convention from the repository root",
                example = "docs/private/conventions/backend/logging.md")
        String conventionPath
) {
}
