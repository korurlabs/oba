package com.korur.oba.apps.kaya.template.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.template.dto.response.TemplateDetailResponse;
import com.korur.oba.apps.kaya.template.dto.response.TemplateListResponse;
import com.korur.oba.apps.kaya.template.entity.Template;

/**
 * Maps {@link Template}s with their size and content to {@link TemplateListResponse}s and
 * {@link TemplateDetailResponse}s.
 */
@Mapper(componentModel = "spring")
public interface TemplateMapper {

    @Mapping(target = "id", source = "template.id")
    @Mapping(target = "versionId", source = "template.versionId")
    @Mapping(target = "path", source = "template.relativePath")
    @Mapping(target = "title", source = "template.title")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    TemplateListResponse toListResponse(Template template, Integer sizeBytes);

    @Mapping(target = "id", source = "template.id")
    @Mapping(target = "versionId", source = "template.versionId")
    @Mapping(target = "path", source = "template.relativePath")
    @Mapping(target = "title", source = "template.title")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "createdAt", source = "template.createdAt")
    @Mapping(target = "updatedAt", source = "template.updatedAt")
    TemplateDetailResponse toDetailResponse(Template template, Integer sizeBytes, String content);
}
