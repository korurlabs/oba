package com.korur.oba.apps.kaya.agentsfile.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.dto.response.AgentsFileDetailResponse;
import com.korur.oba.apps.kaya.agentsfile.dto.response.AgentsFileListResponse;
import com.korur.oba.apps.kaya.agentsfile.entity.AgentsFile;

/**
 * Maps {@link AgentsFile}s with their size and content to {@link AgentsFileListResponse}s and
 * {@link AgentsFileDetailResponse}s.
 */
@Mapper(componentModel = "spring")
public interface AgentsFileMapper {

    @Mapping(target = "id", source = "agentsFile.id")
    @Mapping(target = "versionId", source = "agentsFile.versionId")
    @Mapping(target = "placement", source = "agentsFile.placement")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    AgentsFileListResponse toListResponse(AgentsFile agentsFile, Integer sizeBytes);

    @Mapping(target = "id", source = "agentsFile.id")
    @Mapping(target = "versionId", source = "agentsFile.versionId")
    @Mapping(target = "placement", source = "agentsFile.placement")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "createdAt", source = "agentsFile.createdAt")
    @Mapping(target = "updatedAt", source = "agentsFile.updatedAt")
    AgentsFileDetailResponse toDetailResponse(AgentsFile agentsFile, Integer sizeBytes, String content);
}
