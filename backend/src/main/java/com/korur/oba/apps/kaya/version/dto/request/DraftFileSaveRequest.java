package com.korur.oba.apps.kaya.version.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.apps.kaya.render.enums.FileEncoding;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.version.constant.VersionConstant;

/**
 * One file a draft stores or replaces, handed to
 * {@link com.korur.oba.apps.kaya.version.service.VersionService#saveFile}. The stored file is the
 * content as sent. A symlink is never saved: the link of a skill renders from the skill itself.
 */
@Schema(description = "Adds or replaces one file of a draft")
public record DraftFileSaveRequest(
        @Schema(description = "Path relative to the repository root", requiredMode = Schema.RequiredMode.REQUIRED,
                example = ".agents/rules/core.md")
        @NotBlank(message = VersionConstant.PATH_REQUIRED_MESSAGE)
        @Size(max = ValidationConstant.PATH_MAX_LENGTH)
        String path,

        @Schema(description = "How content is encoded: UTF_8 (the default) or BASE64 for bytes that are not text",
                nullable = true, example = "UTF_8")
        FileEncoding encoding,

        @Schema(description = "Content of the file, at most 5 MiB once decoded", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "# Core rules\n")
        @NotNull(message = VersionConstant.CONTENT_REQUIRED_MESSAGE)
        @Size(max = VersionConstant.CONTENT_MAX_LENGTH, message = VersionConstant.CONTENT_SIZE_MESSAGE)
        String content,

        @Schema(description = "Whether the file is stored with the executable bit; only for a hook or a file of a skill "
                + "directory other than SKILL.md, which is then stored as a script; null means false", nullable = true,
                example = "false")
        Boolean executable,

        @Schema(description = "Inherited path this file overrides; only the marker the file already carries is "
                + "accepted; null when the file overrides nothing", nullable = true,
                example = ".agents/rules/16-kagi-api-first.md")
        @NullOrNotBlank
        @Size(max = ValidationConstant.PATH_MAX_LENGTH)
        String overridesPath
) {
}
