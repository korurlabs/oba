package com.korur.oba.apps.kaya.seed.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.blob.constant.BlobConstant;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;

/**
 * One file of a seeded pack or project, at the path a consumer repository has it. A FILE or EXECUTABLE names
 * content uploaded before through PUT /blobs/{sha256}; a SYMLINK has a target and no hash.
 * A file that breaks a file rule or does not parse is refused and reported, not refused with the request.
 */
@Schema(description = "A file of a seeded set")
public record SeedFileRequest(
        @Schema(description = "Path relative to the repository root", requiredMode = Schema.RequiredMode.REQUIRED,
                example = ".agents/rules/core.md")
        @NotBlank
        @Size(max = ValidationConstant.PATH_MAX_LENGTH)
        String path,

        @Schema(description = "SHA-256 of the content, lower-case hex; required for FILE and EXECUTABLE, absent for SYMLINK",
                nullable = true, example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        @Pattern(regexp = BlobConstant.SHA256_PATTERN, message = BlobConstant.SHA256_INVALID_MESSAGE)
        String sha256,

        @Schema(description = "FILE, EXECUTABLE or SYMLINK", requiredMode = Schema.RequiredMode.REQUIRED, example = "FILE")
        @NotNull
        FileMode mode,

        @Schema(description = "Relative target of a SYMLINK, as the link stores it", nullable = true,
                example = "../../.agents/skills/kazan")
        @Size(max = ValidationConstant.PATH_MAX_LENGTH)
        String symlinkTarget
) {
}
