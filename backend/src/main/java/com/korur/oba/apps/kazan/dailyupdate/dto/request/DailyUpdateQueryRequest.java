package com.korur.oba.apps.kazan.dailyupdate.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.dto.QueryRequest;

/**
 * Query parameters for the daily update feed. The feed is always sorted by
 * creation time, newest first, so sortBy and sortDirection are ignored.
 */
@Getter
@Setter
@NoArgsConstructor
public class DailyUpdateQueryRequest extends QueryRequest {

    /**
     * Filter by author identifiers. Null or empty means every visible author.
     */
    @Schema(description = "Filter by author identifiers", nullable = true)
    private List<UUID> authorIds;

    /**
     * Keep updates with at least one line on these boards. Null or empty means all boards.
     */
    @Schema(description = "Keep updates with at least one line on these boards", nullable = true)
    private List<UUID> boardIds;

    /**
     * Keep updates created on or after this report day (inclusive), a day of the
     * business calendar in Europe/Istanbul. Null means no lower bound.
     */
    @Schema(description = "Created on or after this report day (YYYY-MM-DD, Europe/Istanbul, inclusive)", nullable = true, example = "2026-10-01")
    private LocalDate from;

    /**
     * Keep updates created on or before this report day (inclusive), a day of the
     * business calendar in Europe/Istanbul. Null means no upper bound.
     */
    @Schema(description = "Created on or before this report day (YYYY-MM-DD, Europe/Istanbul, inclusive)", nullable = true, example = "2026-10-19")
    private LocalDate to;
}
