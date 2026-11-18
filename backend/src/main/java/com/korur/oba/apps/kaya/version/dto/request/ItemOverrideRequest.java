package com.korur.oba.apps.kaya.version.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;

/**
 * Moves the base of an override in a draft to the ancestor version the draft's item was reviewed
 * against, which {@link com.korur.oba.apps.kaya.version.service.VersionService#setOverrideBase}
 * accepts only when it is the current version of the nearest ancestor delivering the item.
 */
@Schema(description = "Sets the ancestor version an overriding item of a draft is based on")
public record ItemOverrideRequest(
        @Schema(description = "The item: a file path, or a skill directory ending in a slash; a path inside a skill "
                + "names its skill", requiredMode = Schema.RequiredMode.REQUIRED, example = ".agents/rules/10-backend.md")
        @NotBlank
        @Size(max = ValidationConstant.PATH_MAX_LENGTH)
        String path,

        @Schema(description = "The current version of the nearest ancestor delivering the item",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a")
        @NotNull
        UUID baseVersionId
) {
}
