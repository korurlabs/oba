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
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kalkan.page.constant.KalkanPageConstant;

/**
 * A page written by its path of slugs, as a push writes it: created when the path names no live
 * page, else updated, unless it was changed in Kalkan since the last push.
 */
@Schema(description = "A page to create or update by its path")
public record KalkanPageUpsertRequest(
        @NotBlank(message = "Path is required")
        @Size(max = 1100, message = "Path must be at most 1100 characters")
        @Pattern(regexp = KalkanPageConstant.PATH_PATTERN,
                message = "Path must be one to ten valid slugs joined by a slash, without a leading or trailing slash")
        @Schema(description = "Slugs from the root, joined by a slash", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "isms/kontroller/A.5.1")
        String path,

        @PositiveOrZero(message = "Position must be zero or more")
        @Schema(description = "Order among the siblings; empty keeps it, or appends a new page", nullable = true,
                example = "0")
        Integer position,

        @Schema(description = "Compliance mark; empty keeps it, or false for a new page", nullable = true,
                example = "true")
        Boolean compliance,

        @NotEmpty(message = "At least one translation is required")
        @Schema(description = "Title and body per locale, each locale at most once",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<@Valid @NotNull(message = "A translation must not be null") KalkanPageTranslationRequest> translations,

        @Schema(description = "Write even when the page was changed in Kalkan since the last push", example = "false")
        boolean force
) {
}
