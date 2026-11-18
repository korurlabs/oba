package com.korur.oba.apps.kaya.version.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.version.constant.VersionConstant;

/**
 * The resolutions of a rebase, handed to
 * {@link com.korur.oba.apps.kaya.version.service.VersionService#rebase}.
 */
@Schema(description = "How the proposer resolves the paths that the draft and the current version both changed. "
        + "The body is optional; without it every such path is reported as a conflict")
public record DraftRebaseRequest(
        @Schema(description = "Conflicting paths whose draft content wins over the current version", nullable = true,
                example = "[\".agents/rules/core.md\"]")
        @Size(max = VersionConstant.RESOLUTIONS_MAX)
        List<@NotBlank @Size(max = ValidationConstant.PATH_MAX_LENGTH) String> keepDraft,

        @Schema(description = "Conflicting paths that take the current version's content, or its deletion, "
                + "and drop the draft's edit", nullable = true, example = "[\".claude/settings.json\"]")
        @Size(max = VersionConstant.RESOLUTIONS_MAX)
        List<@NotBlank @Size(max = ValidationConstant.PATH_MAX_LENGTH) String> takeCurrent
) {
}
