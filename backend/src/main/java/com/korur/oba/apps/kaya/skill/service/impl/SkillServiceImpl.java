package com.korur.oba.apps.kaya.skill.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.convention.service.ConventionQueryService;
import com.korur.oba.apps.kaya.reference.service.ReferenceQueryService;
import com.korur.oba.apps.kaya.script.service.ScriptQueryService;
import com.korur.oba.apps.kaya.skill.constant.SkillConstant;
import com.korur.oba.apps.kaya.skill.constant.service.SkillServiceLogConstant;
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillIndex;
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillPartIndex;
import com.korur.oba.apps.kaya.skill.entity.Skill;
import com.korur.oba.apps.kaya.skill.entity.SkillConvention;
import com.korur.oba.apps.kaya.skill.entity.SkillReference;
import com.korur.oba.apps.kaya.skill.entity.SkillScript;
import com.korur.oba.apps.kaya.skill.entity.SkillToolParameter;
import com.korur.oba.apps.kaya.skill.entity.SkillWorkflow;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.skill.repository.SkillRepository;
import com.korur.oba.apps.kaya.skill.service.SkillService;
import com.korur.oba.apps.kaya.toolparameter.service.ToolParameterQueryService;
import com.korur.oba.apps.kaya.workflow.service.WorkflowQueryService;

/**
 * The {@link SkillService} implementation.
 */
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SkillServiceImpl.class);

    private final SkillRepository skillRepository;
    private final WorkflowQueryService workflowQueryService;
    private final ScriptQueryService scriptQueryService;
    private final ConventionQueryService conventionQueryService;
    private final ReferenceQueryService referenceQueryService;
    private final ToolParameterQueryService toolParameterQueryService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Skill> replaceVersion(UUID organizationId, UUID versionId, List<SkillIndex> skills) {
        // Checked before anything is deleted, so a bad link leaves the version's skills as they were.
        requirePartsOfVersion(organizationId, versionId, skills);

        // The old rows are deleted and flushed first, so a skill saved again under the same name does
        // not collide with the old row on the version and name unique key.
        int removed = skillRepository.deleteByVersion(organizationId, versionId);
        List<Skill> entities = skills.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<Skill> saved = skillRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SkillServiceLogConstant.SKILLS_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public Skill saveSkill(UUID organizationId, UUID versionId, SkillIndex skill) {
        requirePartsOfVersion(organizationId, versionId, List.of(skill));
        // The old row is deleted and flushed first, so the new row does not collide with it on the
        // version and name unique key.
        int removed = skillRepository.deleteByName(organizationId, versionId, skill.name());
        Skill saved = skillRepository.save(toEntity(organizationId, versionId, skill));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SkillServiceLogConstant.SKILL_SAVED,
                skill.name(), versionId, organizationId, removed);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteSkill(UUID organizationId, UUID versionId, String name) {
        int removed = skillRepository.deleteByName(organizationId, versionId, name);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SkillServiceLogConstant.SKILL_DELETED,
                name, versionId, organizationId, removed);
        return removed > 0;
    }

    private void requirePartsOfVersion(UUID organizationId, UUID versionId, List<SkillIndex> skills) {
        Map<SkillPartKind, Set<UUID>> versionParts = new EnumMap<>(SkillPartKind.class);
        for (SkillIndex index : skills) {
            for (SkillPartIndex part : partsOf(index)) {
                Set<UUID> partIds = versionParts.computeIfAbsent(part.kind(),
                        kind -> partIdsOfVersion(kind, organizationId, versionId));
                if (!partIds.contains(part.partId())) {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SkillServiceLogConstant.SKILL_PART_NOT_IN_VERSION,
                            index.name(), part.kind(), part.partId(), part.path(), versionId);
                    throw new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                            Map.of(SkillConstant.FIELD_PART_KIND, part.kind().name(),
                                    SkillConstant.FIELD_PART_ID, String.valueOf(part.partId())));
                }
            }
        }
    }

    private static List<SkillPartIndex> partsOf(SkillIndex index) {
        return index.parts() == null ? List.of() : index.parts();
    }

    // The ids of every part of one kind in the version, loaded once per kind and parse.
    private Set<UUID> partIdsOfVersion(SkillPartKind kind, UUID organizationId, UUID versionId) {
        List<? extends BaseEntity> parts = switch (kind) {
            case WORKFLOW -> workflowQueryService.getEntitiesByVersionId(organizationId, versionId);
            case SCRIPT -> scriptQueryService.getEntitiesByVersionId(organizationId, versionId);
            case CONVENTION -> conventionQueryService.getEntitiesByVersionId(organizationId, versionId);
            case REFERENCE -> referenceQueryService.getEntitiesByVersionId(organizationId, versionId);
            case TOOL_PARAMETER -> toolParameterQueryService.getEntitiesByVersionId(organizationId, versionId);
        };
        return parts.stream().map(BaseEntity::getId).collect(Collectors.toSet());
    }

    private Skill toEntity(UUID organizationId, UUID versionId, SkillIndex index) {
        Skill skill = Skill.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .name(index.name())
                .description(index.description())
                .build();
        for (SkillPartIndex part : partsOf(index)) {
            addLink(skill, part);
        }
        return skill;
    }

    private void addLink(Skill skill, SkillPartIndex part) {
        switch (part.kind()) {
            case WORKFLOW -> skill.getWorkflows().add(
                    SkillWorkflow.builder().skill(skill).workflowId(part.partId()).path(part.path()).build());
            case SCRIPT -> skill.getScripts().add(
                    SkillScript.builder().skill(skill).scriptId(part.partId()).path(part.path()).build());
            case CONVENTION -> skill.getConventions().add(
                    SkillConvention.builder().skill(skill).conventionId(part.partId()).path(part.path()).build());
            case REFERENCE -> skill.getReferences().add(
                    SkillReference.builder().skill(skill).referenceId(part.partId()).path(part.path()).build());
            case TOOL_PARAMETER -> skill.getToolParameters().add(
                    SkillToolParameter.builder().skill(skill).toolParameterId(part.partId()).path(part.path()).build());
        }
    }
}
