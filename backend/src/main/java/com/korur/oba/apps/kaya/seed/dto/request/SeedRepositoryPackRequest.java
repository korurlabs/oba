package com.korur.oba.apps.kaya.seed.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.apps.kaya.pack.constant.PackConstant;
import com.korur.oba.apps.kaya.release.constant.ReleaseConstant;

/**
 * One pack a seeded repository uses: at the repository root, or mounted in a folder.
 */
@Schema(description = "A pack a seeded repository uses, at the root or mounted in a folder")
public record SeedRepositoryPackRequest(
        @Schema(description = "Name of the pack", requiredMode = Schema.RequiredMode.REQUIRED, example = "kagi")
        @NotBlank
        @Pattern(regexp = PackConstant.NAME_PATTERN, message = PackConstant.NAME_INVALID_MESSAGE)
        String name,

        @Schema(description = "Repository-relative mount folder; null uses the pack at the repository root", nullable = true,
                example = "projects/kagi")
        @NullOrNotBlank(message = ReleaseConstant.ROOT_PATH_BLANK_MESSAGE)
        @Size(max = ReleaseConstant.ROOT_PATH_MAX_LENGTH)
        String rootPath
) {
}
