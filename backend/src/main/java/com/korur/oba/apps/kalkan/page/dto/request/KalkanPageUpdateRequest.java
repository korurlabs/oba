package com.korur.oba.apps.kalkan.page.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kalkan.page.constant.KalkanPageConstant;

/**
 * A change of a page against the version the editor loaded. An empty field keeps the stored value;
 * only the given locales are written, each created or replaced.
 */
@Schema(description = "A change of a page; empty fields keep the stored values")
public record KalkanPageUpdateRequest(
        @Pattern(regexp = KalkanPageConstant.SLUG_PATTERN,
                message = "Slug must start with a letter or digit and hold only letters, digits, dots, underscores and hyphens, at most 100")
        @Schema(description = "New URL segment; empty to keep it. A root page keeps its slug, so for a root "
                + "only its current slug or empty is accepted", nullable = true, example = "A.5.2")
        String slug,

        @Schema(description = "Compliance mark; empty to keep it", nullable = true, example = "true")
        Boolean compliance,

        @NotNull(message = "Version is required")
        @Schema(description = "The page version the change is based on", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "3")
        Long version,

        @Schema(description = "Title and body of the locales to write, each locale at most once", nullable = true)
        List<@Valid @NotNull(message = "A translation must not be null") KalkanPageTranslationRequest> translations
) {
}
