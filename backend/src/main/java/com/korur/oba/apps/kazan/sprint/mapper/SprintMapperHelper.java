package com.korur.oba.apps.kazan.sprint.mapper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kazan.sprint.dto.response.SprintSeriesResponse;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeries;
import com.korur.oba.apps.kazan.sprint.helper.SprintRecurrenceCalculator;
import com.korur.oba.apps.kazan.sprint.helper.SprintSeriesNaming;
import com.korur.oba.apps.kazan.sprint.repository.SprintRepository;

/**
 * Builds the series part of a sprint's detail response.
 *
 * The next rollover belongs to the series, not to the sprint shown: it is the
 * end date of the series' ACTIVE sprint or, when none runs, the start date of
 * its PLANNED head. Finding those takes one lookup of the series' sprints,
 * which only the detail response pays; sprint lists load nothing extra.
 */
@Component
@RequiredArgsConstructor
public class SprintMapperHelper {

    private final SprintRepository sprintRepository;
    private final SprintSeriesMapper sprintSeriesMapper;
    private final SprintRecurrenceCalculator sprintRecurrenceCalculator;

    /**
     * Maps the series of a sprint, or null when the sprint has none.
     *
     * @param sprint the sprint
     * @return the series response, or null
     */
    @Named("toSeries")
    public SprintSeriesResponse toSeries(Sprint sprint) {
        if (sprint == null || sprint.getSeries() == null) {
            return null;
        }
        SprintSeries series = sprint.getSeries();
        String nextSprintName = SprintSeriesNaming.sprintName(series.getName(), series.getNextRound());
        Instant nextRolloverAt = series.isEnabled()
                ? sprintRecurrenceCalculator.nextRolloverAt(series,
                        sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId()))
                : null;
        return sprintSeriesMapper.toResponse(series, nextSprintName, nextRolloverAt);
    }
}
