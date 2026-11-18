package com.korur.oba.apps.kaya.toolparameter.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.toolparameter.dto.response.ToolParameterDetailResponse;
import com.korur.oba.apps.kaya.toolparameter.entity.ToolParameter;

/**
 * Maps a {@link ToolParameter} with its size and content to a {@link ToolParameterDetailResponse}.
 */
@Mapper(componentModel = "spring")
public interface ToolParameterMapper {

    @Mapping(target = "id", source = "toolParameter.id")
    @Mapping(target = "versionId", source = "toolParameter.versionId")
    @Mapping(target = "name", source = "toolParameter.name")
    @Mapping(target = "format", source = "toolParameter.format")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "createdAt", source = "toolParameter.createdAt")
    @Mapping(target = "updatedAt", source = "toolParameter.updatedAt")
    ToolParameterDetailResponse toDetailResponse(ToolParameter toolParameter, Integer sizeBytes, String content);
}
