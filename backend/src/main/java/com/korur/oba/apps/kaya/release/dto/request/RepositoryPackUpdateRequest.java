package com.korur.oba.apps.kaya.release.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.release.constant.ReleaseConstant;

/**
 * The full pack list of a repository, replacing the stored one; an empty list composes no pack of
 * its own.
 */
@Schema(description = "The packs a repository composes, in display order, each at the root or in a folder")
public record RepositoryPackUpdateRequest(
        @Schema(description = "The packs in display order, 0 to 50; an empty list composes no pack of the repository's own",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Size(max = ReleaseConstant.PACKS_MAX)
        List<@NotNull @Valid RepositoryPackItemRequest> packs
) {
}
