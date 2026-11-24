package com.korur.oba.apps.kalkan.page.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;

/**
 * The query of the trash: which locale's titles to show.
 */
@Getter
@Setter
@NoArgsConstructor
public class KalkanPageTrashRequest {

    /**
     * Locale of the titles; without it each page shows its first title.
     */
    @Schema(description = "Locale of the titles; without it each page shows its first title", nullable = true,
            example = "EN")
    private KalkanLocale locale;
}
