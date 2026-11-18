package com.korur.oba.apps.kaya.skill.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillPartLink;
import com.korur.oba.apps.kaya.skill.dto.response.SkillDetailResponse;
import com.korur.oba.apps.kaya.skill.dto.response.SkillListResponse;
import com.korur.oba.apps.kaya.skill.dto.response.embedded.SkillPartEmbeddedResponse;
import com.korur.oba.apps.kaya.skill.entity.Skill;
import com.korur.oba.apps.kaya.skill.entity.SkillConvention;
import com.korur.oba.apps.kaya.skill.entity.SkillReference;
import com.korur.oba.apps.kaya.skill.entity.SkillScript;
import com.korur.oba.apps.kaya.skill.entity.SkillToolParameter;
import com.korur.oba.apps.kaya.skill.entity.SkillWorkflow;

/**
 * Maps {@link Skill}s with their size, content and part links to {@link SkillListResponse}s,
 * {@link SkillDetailResponse}s and {@link SkillPartEmbeddedResponse}s.
 */
@Mapper(componentModel = "spring")
public interface SkillMapper {

    @Mapping(target = "id", source = "skill.id")
    @Mapping(target = "versionId", source = "skill.versionId")
    @Mapping(target = "path", source = "skill.name")
    @Mapping(target = "name", source = "skill.name")
    @Mapping(target = "description", source = "skill.description")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    SkillListResponse toListResponse(Skill skill, Integer sizeBytes);

    @Mapping(target = "id", source = "skill.id")
    @Mapping(target = "versionId", source = "skill.versionId")
    @Mapping(target = "path", source = "skill.name")
    @Mapping(target = "name", source = "skill.name")
    @Mapping(target = "description", source = "skill.description")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "parts", source = "parts")
    @Mapping(target = "createdAt", source = "skill.createdAt")
    @Mapping(target = "updatedAt", source = "skill.updatedAt")
    SkillDetailResponse toDetailResponse(Skill skill, Integer sizeBytes, String content,
            List<SkillPartEmbeddedResponse> parts);

    @Mapping(target = "kind", constant = "WORKFLOW")
    @Mapping(target = "id", source = "link.workflowId")
    @Mapping(target = "path", source = "link.path")
    @Mapping(target = "usedBySkills", source = "usedBySkills")
    SkillPartEmbeddedResponse toPartEmbeddedResponse(SkillWorkflow link, Integer usedBySkills);

    @Mapping(target = "kind", constant = "SCRIPT")
    @Mapping(target = "id", source = "link.scriptId")
    @Mapping(target = "path", source = "link.path")
    @Mapping(target = "usedBySkills", source = "usedBySkills")
    SkillPartEmbeddedResponse toPartEmbeddedResponse(SkillScript link, Integer usedBySkills);

    @Mapping(target = "kind", constant = "CONVENTION")
    @Mapping(target = "id", source = "link.conventionId")
    @Mapping(target = "path", source = "link.path")
    @Mapping(target = "usedBySkills", source = "usedBySkills")
    SkillPartEmbeddedResponse toPartEmbeddedResponse(SkillConvention link, Integer usedBySkills);

    @Mapping(target = "kind", constant = "REFERENCE")
    @Mapping(target = "id", source = "link.referenceId")
    @Mapping(target = "path", source = "link.path")
    @Mapping(target = "usedBySkills", source = "usedBySkills")
    SkillPartEmbeddedResponse toPartEmbeddedResponse(SkillReference link, Integer usedBySkills);

    @Mapping(target = "kind", constant = "TOOL_PARAMETER")
    @Mapping(target = "id", source = "link.toolParameterId")
    @Mapping(target = "path", source = "link.path")
    @Mapping(target = "usedBySkills", source = "usedBySkills")
    SkillPartEmbeddedResponse toPartEmbeddedResponse(SkillToolParameter link, Integer usedBySkills);

    @Mapping(target = "skillId", source = "skill.id")
    @Mapping(target = "skillName", source = "skill.name")
    @Mapping(target = "versionId", source = "skill.versionId")
    @Mapping(target = "kind", constant = "WORKFLOW")
    @Mapping(target = "partId", source = "workflowId")
    @Mapping(target = "path", source = "path")
    SkillPartLink toPartLink(SkillWorkflow link);

    @Mapping(target = "skillId", source = "skill.id")
    @Mapping(target = "skillName", source = "skill.name")
    @Mapping(target = "versionId", source = "skill.versionId")
    @Mapping(target = "kind", constant = "SCRIPT")
    @Mapping(target = "partId", source = "scriptId")
    @Mapping(target = "path", source = "path")
    SkillPartLink toPartLink(SkillScript link);

    @Mapping(target = "skillId", source = "skill.id")
    @Mapping(target = "skillName", source = "skill.name")
    @Mapping(target = "versionId", source = "skill.versionId")
    @Mapping(target = "kind", constant = "CONVENTION")
    @Mapping(target = "partId", source = "conventionId")
    @Mapping(target = "path", source = "path")
    SkillPartLink toPartLink(SkillConvention link);

    @Mapping(target = "skillId", source = "skill.id")
    @Mapping(target = "skillName", source = "skill.name")
    @Mapping(target = "versionId", source = "skill.versionId")
    @Mapping(target = "kind", constant = "REFERENCE")
    @Mapping(target = "partId", source = "referenceId")
    @Mapping(target = "path", source = "path")
    SkillPartLink toPartLink(SkillReference link);

    @Mapping(target = "skillId", source = "skill.id")
    @Mapping(target = "skillName", source = "skill.name")
    @Mapping(target = "versionId", source = "skill.versionId")
    @Mapping(target = "kind", constant = "TOOL_PARAMETER")
    @Mapping(target = "partId", source = "toolParameterId")
    @Mapping(target = "path", source = "path")
    SkillPartLink toPartLink(SkillToolParameter link);
}
