package com.korur.oba.apps.otak.event.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.enums.EventType;
import com.korur.oba.apps.otak.relation.enums.Country;

/**
 * One row of the event list.
 */
@Schema(description = "Event list item")
public record EventListResponse(

        @Schema(description = "Event id", example = "8a1d3c5e-2b4f-4e6a-9c7d-1f2e3a4b5c6d")
        UUID id,

        @Schema(description = "Name of the event", example = "IGEF 2026")
        String name,

        @Schema(description = "Kind of event", example = "EXPO")
        EventType type,

        @Schema(description = "First day of the event (YYYY-MM-DD)", example = "2026-11-19")
        LocalDate startDate,

        @Schema(description = "Last day of the event (YYYY-MM-DD); null for a one-day event", nullable = true,
                example = "2026-11-22")
        LocalDate endDate,

        @Schema(description = "City", nullable = true, example = "Ankara")
        String city,

        @Schema(description = "ISO 3166-1 alpha-2 country code", nullable = true, example = "TR")
        Country country,

        @Schema(description = "Website", nullable = true, example = "https://igef.com.tr/")
        String website
) {
}
