package com.korur.oba.apps.kaya.workflow.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.workflow.dto.response.WorkflowDetailResponse;
import com.korur.oba.apps.kaya.workflow.dto.response.WorkflowListResponse;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;

/**
 * Maps {@link Workflow}s with their size and content to {@link WorkflowListResponse}s and
 * {@link WorkflowDetailResponse}s.
 */
@Mapper(componentModel = "spring")
public interface WorkflowMapper {

    @Mapping(target = "id", source = "workflow.id")
    @Mapping(target = "versionId", source = "workflow.versionId")
    @Mapping(target = "path", source = "workflow.relativePath")
    @Mapping(target = "title", source = "workflow.title")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    WorkflowListResponse toListResponse(Workflow workflow, Integer sizeBytes);

    @Mapping(target = "id", source = "workflow.id")
    @Mapping(target = "versionId", source = "workflow.versionId")
    @Mapping(target = "path", source = "workflow.relativePath")
    @Mapping(target = "title", source = "workflow.title")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "createdAt", source = "workflow.createdAt")
    @Mapping(target = "updatedAt", source = "workflow.updatedAt")
    WorkflowDetailResponse toDetailResponse(Workflow workflow, Integer sizeBytes, String content);
}
