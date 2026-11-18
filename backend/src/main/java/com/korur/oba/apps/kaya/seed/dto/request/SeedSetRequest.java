package com.korur.oba.apps.kaya.seed.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.pack.constant.PackConstant;
import com.korur.oba.apps.kaya.seed.constant.SeedConstant;
import com.korur.oba.apps.kaya.seed.enums.SeedSetKind;

/**
 * One set of a seed: a pack with its parent and the complete content of its files; a file left out
 * is removed from the next version. A pack's parent is fixed when the pack is created, so a set
 * that names another parent than the stored one refuses the run.
 */
@Schema(description = "A set of a seed: a pack, its parent and its files")
public record SeedSetRequest(
        @Schema(description = "PACK", requiredMode = Schema.RequiredMode.REQUIRED, example = "PACK")
        @NotNull
        SeedSetKind kind,

        @Schema(description = "Name of the pack", requiredMode = Schema.RequiredMode.REQUIRED, example = "kagi")
        @NotBlank
        @Pattern(regexp = PackConstant.NAME_PATTERN, message = PackConstant.NAME_INVALID_MESSAGE)
        String name,

        @Schema(description = "Name of the parent pack, a set of the request or a pack of the organization; null for a root pack",
                nullable = true, example = "village")
        @Pattern(regexp = PackConstant.NAME_PATTERN, message = PackConstant.NAME_INVALID_MESSAGE)
        String parent,

        @Schema(description = "Every file of the set", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Size(max = SeedConstant.FILES_PER_SET_MAX)
        List<@NotNull @Valid SeedFileRequest> files
) {
}
