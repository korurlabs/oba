package com.korur.oba.apps.kaya.version.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Starts a draft of one pack, which
 * {@link com.korur.oba.apps.kaya.version.service.VersionService#createDraft} copies from its current
 * version.
 */
@Schema(description = "Starts a draft of a pack as a copy of its current version")
public record DraftCreateRequest(
        @Schema(description = "Pack to draft", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
        @NotNull
        UUID packId
) {
}
