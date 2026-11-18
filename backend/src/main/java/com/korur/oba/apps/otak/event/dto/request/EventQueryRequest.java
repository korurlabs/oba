package com.korur.oba.apps.otak.event.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.time.LocalDate;

// 4. Internal
import com.korur.oba.apps.otak.event.constant.EventConstant;
import com.korur.oba.apps.otak.event.enums.EventPeriod;
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.dojo.dto.QueryRequest;

/**
 * Filters of the event list. The order follows the period, so sortBy and sortDirection
 * are ignored. Upcoming and past are decided on the viewer's calendar day, which only the
 * client knows, so a period needs today.
 */
@Schema(description = "Paging and filters of the event list; sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class EventQueryRequest extends QueryRequest {

    @Schema(description = "UPCOMING or PAST relative to today; null for every event, most recent start first",
            nullable = true, example = "UPCOMING")
    private EventPeriod period;

    @Schema(description = "The viewer's calendar day (YYYY-MM-DD); required with period", nullable = true,
            example = "2026-11-14")
    private LocalDate today;

    @Schema(description = "Part of the name, case ignored; absent for every event", nullable = true,
            example = "igef")
    @NullOrNotBlank(message = EventConstant.SEARCH_BLANK_MESSAGE)
    @Size(max = EventConstant.SEARCH_MAX_LENGTH, message = EventConstant.SEARCH_SIZE_MESSAGE)
    private String search;
}
