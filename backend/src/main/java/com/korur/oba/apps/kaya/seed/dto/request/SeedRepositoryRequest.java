package com.korur.oba.apps.kaya.seed.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.apps.kaya.release.constant.ReleaseConstant;
import com.korur.oba.apps.kaya.seed.constant.SeedConstant;

/**
 * A repository a seed connects and the full list of packs it uses, each at its root or mounted in
 * a folder. The list replaces the stored one, as a pack list change in the web app does.
 */
@Schema(description = "A repository of a seed and the packs it uses")
public record SeedRepositoryRequest(
        @Schema(description = "Git remote of the repository", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "git@github.com:korurlabs/village.git")
        @NotBlank
        @Size(max = SeedConstant.REMOTE_URL_MAX_LENGTH)
        String remoteUrl,

        @Schema(description = "Name of the repository, used only when the seed connects it", nullable = true, example = "village")
        @NullOrNotBlank
        @Size(max = SeedConstant.REPOSITORY_NAME_MAX_LENGTH)
        String name,

        @Schema(description = "The packs in display order, each a set of the request or a pack of the organization",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Size(max = ReleaseConstant.PACKS_MAX)
        List<@NotNull @Valid SeedRepositoryPackRequest> packs
) {
}
