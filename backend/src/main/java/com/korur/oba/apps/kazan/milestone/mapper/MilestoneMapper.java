package com.korur.oba.apps.kazan.milestone.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.BeanMapping;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.dto.request.MilestoneCreateRequest;
import com.korur.oba.apps.kazan.milestone.dto.request.MilestoneUpdateRequest;
import com.korur.oba.apps.kazan.milestone.dto.response.MilestoneDependencyResponse;
import com.korur.oba.apps.kazan.milestone.dto.response.MilestoneDetailResponse;
import com.korur.oba.apps.kazan.milestone.dto.response.MilestoneListResponse;
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneHealth;

/**
 * Maps milestone entities to DTOs and applies requests to entities.
 *
 * Counts, health and dependencies are derived values passed in by MilestoneHelper; the board
 * relation is set by the service from the access check.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface MilestoneMapper {

    /**
     * Maps a create request to a new entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "tickets", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Milestone toEntity(MilestoneCreateRequest request);

    /**
     * Applies the provided fields of an update request; null fields are left unchanged.
     * The clear flags are applied by the service.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "tickets", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Milestone milestone, MilestoneUpdateRequest request);

    /**
     * Maps a milestone a dependent depends on, with the derived conflict flag.
     */
    @Mapping(target = "milestoneId", source = "dependsOn.id")
    @Mapping(target = "name", source = "dependsOn.name")
    @Mapping(target = "targetDate", source = "dependsOn.targetDate")
    @Mapping(target = "boardId", source = "dependsOn.board.id")
    @Mapping(target = "conflict", source = "conflict")
    MilestoneDependencyResponse toDependencyResponse(Milestone dependsOn, boolean conflict);

    /**
     * Maps a milestone with its derived progress, health and dependencies to a list response.
     */
    @Mapping(target = "boardId", source = "milestone.board.id")
    @Mapping(target = "boardName", source = "milestone.board.name")
    @Mapping(target = "boardPrefix", source = "milestone.board.prefix")
    @Mapping(target = "doneCount", source = "doneCount")
    @Mapping(target = "totalCount", source = "totalCount")
    @Mapping(target = "health", source = "health")
    @Mapping(target = "dependsOn", source = "dependsOn")
    MilestoneListResponse toListResponse(Milestone milestone, long doneCount, long totalCount, MilestoneHealth health,
            List<MilestoneDependencyResponse> dependsOn);

    /**
     * Maps a milestone with its derived progress, health and dependencies to a detail response.
     */
    @Mapping(target = "boardId", source = "milestone.board.id")
    @Mapping(target = "boardName", source = "milestone.board.name")
    @Mapping(target = "boardPrefix", source = "milestone.board.prefix")
    @Mapping(target = "doneCount", source = "doneCount")
    @Mapping(target = "totalCount", source = "totalCount")
    @Mapping(target = "health", source = "health")
    @Mapping(target = "dependsOn", source = "dependsOn")
    MilestoneDetailResponse toDetailResponse(Milestone milestone, long doneCount, long totalCount, MilestoneHealth health,
            List<MilestoneDependencyResponse> dependsOn);
}
