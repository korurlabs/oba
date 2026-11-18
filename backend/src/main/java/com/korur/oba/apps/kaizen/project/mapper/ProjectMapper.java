package com.korur.oba.apps.kaizen.project.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.BeanMapping;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.korur.oba.apps.kaizen.project.dto.request.ProjectCreateRequest;
import com.korur.oba.apps.kaizen.project.dto.request.ProjectUpdateRequest;
import com.korur.oba.apps.kaizen.project.dto.response.ProjectDetailResponse;
import com.korur.oba.apps.kaizen.project.dto.response.ProjectListResponse;
import com.korur.oba.apps.kaizen.project.entity.Project;

// 3. Java SDK

// 4. Internal

/**
 * Maps project entities to DTOs and applies update requests to entities.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ProjectMapper {

    /**
     * Maps a project creation request to a new entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "tickets", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Project toEntity(ProjectCreateRequest request);

    /**
     * Maps a project entity to a list response DTO.
     */
    @Mapping(source = "board.id", target = "boardId")
    @Mapping(source = "board.name", target = "boardName")
    ProjectListResponse toListResponse(Project project);

    /**
     * Maps a project entity to a detail response DTO.
     */
    @Mapping(source = "board.id", target = "boardId")
    ProjectDetailResponse toDetailResponse(Project project);

    /**
     * Updates an existing project entity from an update request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "tickets", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Project project, ProjectUpdateRequest request);
}
