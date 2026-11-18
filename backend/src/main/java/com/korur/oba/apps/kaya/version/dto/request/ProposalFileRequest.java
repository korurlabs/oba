package com.korur.oba.apps.kaya.version.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileEncoding;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.version.constant.VersionConstant;

/**
 * One file of a CLI proposal: its new state, or its removal.
 */
@Schema(description = "One file of a proposal: its new state, or its removal")
public record ProposalFileRequest(
        @Schema(description = "Path relative to the repository root", requiredMode = Schema.RequiredMode.REQUIRED,
                example = ".agents/rules/core.md")
        @NotBlank(message = VersionConstant.PATH_REQUIRED_MESSAGE)
        @Size(max = ValidationConstant.PATH_MAX_LENGTH)
        String path,

        @Schema(description = "FILE, EXECUTABLE or SYMLINK; required unless deleted is true", nullable = true, example = "FILE")
        FileMode mode,

        @Schema(description = "How content is encoded: UTF_8 (the default) or BASE64 for bytes that are not text",
                nullable = true, example = "UTF_8")
        FileEncoding encoding,

        @Schema(description = "Content of a FILE or EXECUTABLE, at most 5 MiB once decoded; null for a SYMLINK or a removal",
                nullable = true, example = "# Core rules\n")
        @Size(max = VersionConstant.CONTENT_MAX_LENGTH, message = VersionConstant.CONTENT_SIZE_MESSAGE)
        String content,

        @Schema(description = "Relative target of a SYMLINK; null otherwise", nullable = true, example = "../../.agents/skills/kazan")
        @Size(max = ValidationConstant.PATH_MAX_LENGTH)
        String symlinkTarget,

        @Schema(description = "True to remove the path from the pack or project; the other fields are then ignored", nullable = true,
                example = "false")
        Boolean deleted
) {

    /** Whether the proposal removes the path. */
    public boolean removes() {
        return Boolean.TRUE.equals(deleted);
    }
}
