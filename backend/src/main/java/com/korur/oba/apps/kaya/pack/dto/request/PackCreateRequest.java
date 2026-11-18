package com.korur.oba.apps.kaya.pack.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.apps.kaya.pack.constant.PackConstant;

/**
 * The body of a pack creation: its name, an optional description and, for a child pack, its parent. A new pack has no
 * version, and its parent cannot be changed later.
 */
@Schema(description = "A new pack of the organization, without versions")
public record PackCreateRequest(
        @Schema(description = "Name, unique among the organization's packs", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "family-java")
        @NotBlank
        @Pattern(regexp = PackConstant.NAME_PATTERN, message = PackConstant.NAME_INVALID_MESSAGE)
        String name,

        @Schema(description = "What the pack is for, plain text; null for none", nullable = true,
                example = "Java conventions shared by every backend repository")
        @NullOrNotBlank(message = PackConstant.DESCRIPTION_BLANK_MESSAGE)
        @Size(max = PackConstant.DESCRIPTION_MAX_LENGTH, message = PackConstant.DESCRIPTION_SIZE_MESSAGE)
        String description,

        @Schema(description = "The parent pack of a child pack, of the same organization; null for a root pack. "
                + "Fixed at creation", nullable = true, example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        UUID parentPackId
) {
}
