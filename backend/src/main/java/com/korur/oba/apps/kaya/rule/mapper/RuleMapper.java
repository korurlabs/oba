package com.korur.oba.apps.kaya.rule.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.rule.dto.response.RuleDetailResponse;
import com.korur.oba.apps.kaya.rule.dto.response.RuleListResponse;
import com.korur.oba.apps.kaya.rule.dto.response.embedded.RuleConventionEmbeddedResponse;
import com.korur.oba.apps.kaya.rule.entity.Rule;
import com.korur.oba.apps.kaya.rule.entity.RuleConvention;

/**
 * Maps {@link Rule}s with their {@link RuleConvention}s, size and content to {@link RuleListResponse}s
 * and {@link RuleDetailResponse}s.
 */
@Mapper(componentModel = "spring")
public interface RuleMapper {

    @Mapping(target = "id", source = "rule.id")
    @Mapping(target = "versionId", source = "rule.versionId")
    @Mapping(target = "path", source = "rule.relativePath")
    @Mapping(target = "description", source = "rule.description")
    @Mapping(target = "paths", source = "rule.paths")
    @Mapping(target = "alwaysOn", source = "rule.alwaysOn")
    @Mapping(target = "overridesPath", source = "rule.overridesPath")
    @Mapping(target = "conventions", source = "conventions")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    RuleListResponse toListResponse(Rule rule, List<RuleConvention> conventions, Integer sizeBytes);

    @Mapping(target = "id", source = "rule.id")
    @Mapping(target = "versionId", source = "rule.versionId")
    @Mapping(target = "path", source = "rule.relativePath")
    @Mapping(target = "description", source = "rule.description")
    @Mapping(target = "paths", source = "rule.paths")
    @Mapping(target = "alwaysOn", source = "rule.alwaysOn")
    @Mapping(target = "overridesPath", source = "rule.overridesPath")
    @Mapping(target = "conventions", source = "conventions")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "createdAt", source = "rule.createdAt")
    @Mapping(target = "updatedAt", source = "rule.updatedAt")
    RuleDetailResponse toDetailResponse(Rule rule, List<RuleConvention> conventions, Integer sizeBytes,
            String content);

    RuleConventionEmbeddedResponse toConventionEmbeddedResponse(RuleConvention link);
}
