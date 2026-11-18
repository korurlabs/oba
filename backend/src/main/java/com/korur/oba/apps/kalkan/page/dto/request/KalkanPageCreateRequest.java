package com.korur.oba.apps.kalkan.page.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.constant.KalkanPageConstant;

/**
 * A new page: a root page when no parent is given, else a child of the parent.
 */
@Schema(description = "A new page; a root page when parentId is empty")
public record KalkanPageCreateRequest(
        @Schema(description = "Parent page; empty for a root page", nullable = true,
                example = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
        UUID parentId,

        @NotBlank(message = "Slug is required")
        @Pattern(regexp = KalkanPageConstant.SLUG_PATTERN,
                message = "Slug must start with a letter or digit and hold only letters, digits, dots, underscores and hyphens, at most 100")
        @Schema(description = "URL segment, unique among the live pages of the same parent",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "A.5.1")
        String slug,

        @PositiveOrZero(message = "Position must be zero or more")
        @Schema(description = "Order among the siblings; empty to append after the last", nullable = true,
                example = "0")
        Integer position,

        @Schema(description = "Whether the page is a compliance document", example = "false")
        boolean compliance,

        @NotEmpty(message = "At least one translation is required")
        @Schema(description = "Title and body per locale, each locale at most once",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<@Valid @NotNull(message = "A translation must not be null") KalkanPageTranslationRequest> translations
) {
}
