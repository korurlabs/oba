package com.korur.oba.apps.kaizen.sprint.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kaizen.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kaizen.sprint.dto.request.SprintSeriesRequest;
import com.korur.oba.apps.kaizen.sprint.dto.response.SprintSeriesResponse;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeries;

/**
 * Maps sprint series settings between requests, entities and responses.
 *
 * The name, the counter, the boards, the schedule, the run state and
 * createDaysBefore outside DAYS_BEFORE are left to the series service, which
 * knows the sprint the request was made from.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface SprintSeriesMapper {

    /**
     * Maps a series request to a new entity without a name, counter or boards.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "nextRound", ignore = true)
    @Mapping(target = "boards", ignore = true)
    @Mapping(target = "nextCreationAt", ignore = true)
    @Mapping(target = "lastRunAt", ignore = true)
    @Mapping(target = "lastRunStatus", ignore = true)
    @Mapping(target = "lastRunMessage", ignore = true)
    SprintSeries toEntity(SprintSeriesRequest request);

    /**
     * Replaces the stored settings with the request's; a null createDaysBefore
     * clears the stored value.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "nextRound", ignore = true)
    @Mapping(target = "boards", ignore = true)
    @Mapping(target = "nextCreationAt", ignore = true)
    @Mapping(target = "lastRunAt", ignore = true)
    @Mapping(target = "lastRunStatus", ignore = true)
    @Mapping(target = "lastRunMessage", ignore = true)
    void updateEntity(@MappingTarget SprintSeries series, SprintSeriesRequest request);

    /**
     * Maps a series to its response with the values derived from its sprints.
     *
     * @param series         the stored series
     * @param nextSprintName name the next sprint of the series gets
     * @param nextRolloverAt next time the job closes or starts a sprint of the series, nullable
     */
    @Mapping(target = "nextSprintName", source = "nextSprintName")
    @Mapping(target = "nextRolloverAt", source = "nextRolloverAt")
    @Mapping(target = "timeZone", constant = SprintRecurrenceConstant.TIME_ZONE)
    SprintSeriesResponse toResponse(SprintSeries series, String nextSprintName, Instant nextRolloverAt);
}
