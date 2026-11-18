package com.korur.oba.apps.kaya.pack.dto.request;

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

/**
 * The body of a pack update: the name, under the same rules as a new pack's name, and the
 * description. Both are written as given, so a null description removes it.
 */
@Schema(description = "The name and description of a pack; the name changes only while nothing uses the pack")
public record PackUpdateRequest(
        @Schema(description = "Name, unique among the organization's packs", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "family-java")
        @NotBlank
        @Pattern(regexp = PackConstant.NAME_PATTERN, message = PackConstant.NAME_INVALID_MESSAGE)
        String name,

        @Schema(description = "What the pack is for, plain text; null removes it", nullable = true,
                example = "Java conventions shared by every backend repository")
        @NullOrNotBlank(message = PackConstant.DESCRIPTION_BLANK_MESSAGE)
        @Size(max = PackConstant.DESCRIPTION_MAX_LENGTH, message = PackConstant.DESCRIPTION_SIZE_MESSAGE)
        String description
) {
}
