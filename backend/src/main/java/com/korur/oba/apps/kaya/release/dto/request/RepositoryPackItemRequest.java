package com.korur.oba.apps.kaya.release.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.apps.kaya.release.constant.ReleaseConstant;

/**
 * One pack of a repository's pack list: composed at the root, or mounted in a folder.
 */
@Schema(description = "A pack a repository composes, at the root or mounted in a folder")
public record RepositoryPackItemRequest(
        @Schema(description = "The pack", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "1d2e3f4a-5b6c-4d7e-8f9a-0b1c2d3e4f5a")
        @NotNull
        UUID packId,

        @Schema(description = "Repository-relative mount folder, 1 to 4 segments, inside or around no other mount's folder "
                + "and not below .agents, .claude or docs; null composes the pack at the repository root",
                nullable = true, example = "projects/kagi")
        @NullOrNotBlank(message = ReleaseConstant.ROOT_PATH_BLANK_MESSAGE)
        @Size(max = ReleaseConstant.ROOT_PATH_MAX_LENGTH)
        String rootPath
) {
}
