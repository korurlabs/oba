package com.korur.oba.apps.kaya.convention.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.convention.dto.response.ConventionDetailResponse;
import com.korur.oba.apps.kaya.convention.dto.response.ConventionListResponse;
import com.korur.oba.apps.kaya.convention.entity.Convention;

/**
 * Maps {@link Convention}s with their size and content to {@link ConventionListResponse}s and
 * {@link ConventionDetailResponse}s.
 */
@Mapper(componentModel = "spring")
public interface ConventionMapper {

    @Mapping(target = "id", source = "convention.id")
    @Mapping(target = "versionId", source = "convention.versionId")
    @Mapping(target = "path", source = "convention.relativePath")
    @Mapping(target = "title", source = "convention.title")
    @Mapping(target = "overridesPath", source = "convention.overridesPath")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    ConventionListResponse toListResponse(Convention convention, Integer sizeBytes);

    @Mapping(target = "id", source = "convention.id")
    @Mapping(target = "versionId", source = "convention.versionId")
    @Mapping(target = "path", source = "convention.relativePath")
    @Mapping(target = "title", source = "convention.title")
    @Mapping(target = "overridesPath", source = "convention.overridesPath")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "createdAt", source = "convention.createdAt")
    @Mapping(target = "updatedAt", source = "convention.updatedAt")
    ConventionDetailResponse toDetailResponse(Convention convention, Integer sizeBytes, String content);
}
