package com.korur.oba.apps.kalkan.page.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kalkan.page.constant.KalkanPageConstant;
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;

/**
 * The query of the recently changed pages: how many to list and which locale's titles to show.
 */
@Getter
@Setter
@NoArgsConstructor
public class KalkanPageRecentRequest {

    /**
     * Most pages to return; bounded so the list stays a quick read and its query a bounded one.
     */
    @Schema(description = "Most pages to return, 1 to 100", defaultValue = "20", example = "20")
    @Min(value = KalkanPageConstant.RECENT_MIN_LIMIT, message = "Limit must be at least 1")
    @Max(value = KalkanPageConstant.RECENT_MAX_LIMIT, message = "Limit must be at most 100")
    private int limit = KalkanPageConstant.RECENT_DEFAULT_LIMIT;

    /**
     * Locale of the titles; without it each page shows its first title.
     */
    @Schema(description = "Locale of the titles; without it each page shows its first title", nullable = true,
            example = "EN")
    private KalkanLocale locale;
}
