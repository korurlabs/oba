package com.korur.oba.apps.kaya.skill.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.skill.constant.SkillConstant;
import com.korur.oba.apps.kaya.skill.constant.service.SkillServiceLogConstant;
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillPartLink;
import com.korur.oba.apps.kaya.skill.dto.projection.SkillPartCountProjection;
import com.korur.oba.apps.kaya.skill.dto.projection.SkillPartUsageProjection;
import com.korur.oba.apps.kaya.skill.dto.request.SkillQueryRequest;
import com.korur.oba.apps.kaya.skill.dto.response.SkillDetailResponse;
import com.korur.oba.apps.kaya.skill.dto.response.SkillListResponse;
import com.korur.oba.apps.kaya.skill.dto.response.embedded.SkillPartEmbeddedResponse;
import com.korur.oba.apps.kaya.skill.entity.Skill;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.skill.mapper.SkillMapper;
import com.korur.oba.apps.kaya.skill.repository.SkillConventionRepository;
import com.korur.oba.apps.kaya.skill.repository.SkillReferenceRepository;
import com.korur.oba.apps.kaya.skill.repository.SkillRepository;
import com.korur.oba.apps.kaya.skill.repository.SkillScriptRepository;
import com.korur.oba.apps.kaya.skill.repository.SkillToolParameterRepository;
import com.korur.oba.apps.kaya.skill.repository.SkillWorkflowRepository;
import com.korur.oba.apps.kaya.skill.service.SkillQueryService;
import com.korur.oba.apps.kaya.skill.specification.SkillSpecification;

/**
 * The {@link SkillQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillQueryServiceImpl implements SkillQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SkillQueryServiceImpl.class);

    // Version first, so the skills of one version stay together when a page lists several.
    private static final Sort LIST_SORT = Sort.by(SkillConstant.FIELD_VERSION_ID, SkillConstant.FIELD_NAME,
            SkillConstant.FIELD_ID);

    private final SkillRepository skillRepository;
    private final SkillWorkflowRepository skillWorkflowRepository;
    private final SkillScriptRepository skillScriptRepository;
    private final SkillConventionRepository skillConventionRepository;
    private final SkillReferenceRepository skillReferenceRepository;
    private final SkillToolParameterRepository skillToolParameterRepository;
    private final BlobQueryService blobQueryService;
    private final SkillMapper skillMapper;
    private final LoggingService loggingService;

    @Override
    public List<SkillListResponse> getSkills(UUID organizationId, SkillQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SkillServiceLogConstant.LISTING_SKILLS, organizationId, request);
        List<Skill> skills = skillRepository.findAll(SkillSpecification.filterBy(organizationId, request), LIST_SORT);
        if (skills.isEmpty()) {
            return List.of();
        }

        // The sizes are read in one query, not once per skill.
        Map<UUID, Integer> sizes = blobQueryService.getSizesByIds(organizationId,
                skills.stream().map(Skill::getContentBlobId).collect(Collectors.toSet()));
        return skills.stream()
                .map(skill -> skillMapper.toListResponse(skill, sizes.get(skill.getContentBlobId())))
                .toList();
    }

    @Override
    public SkillDetailResponse getSkill(UUID organizationId, UUID skillId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SkillServiceLogConstant.READING_SKILL, skillId, organizationId);
        Skill skill = getEntityById(organizationId, skillId);
        Blob blob = blobQueryService.getEntityById(organizationId, skill.getContentBlobId());

        List<SkillPartEmbeddedResponse> parts = new ArrayList<>();
        parts.addAll(parts(skillWorkflowRepository.findBySkill_IdOrderByPathAsc(skillId),
                link -> link.getWorkflowId(), skillWorkflowRepository::countSkillsByWorkflowIds,
                skillMapper::toPartEmbeddedResponse));
        parts.addAll(parts(skillScriptRepository.findBySkill_IdOrderByPathAsc(skillId),
                link -> link.getScriptId(), skillScriptRepository::countSkillsByScriptIds,
                skillMapper::toPartEmbeddedResponse));
        parts.addAll(parts(skillConventionRepository.findBySkill_IdOrderByPathAsc(skillId),
                link -> link.getConventionId(), skillConventionRepository::countSkillsByConventionIds,
                skillMapper::toPartEmbeddedResponse));
        parts.addAll(parts(skillReferenceRepository.findBySkill_IdOrderByPathAsc(skillId),
                link -> link.getReferenceId(), skillReferenceRepository::countSkillsByReferenceIds,
                skillMapper::toPartEmbeddedResponse));
        parts.addAll(parts(skillToolParameterRepository.findBySkill_IdOrderByPathAsc(skillId),
                link -> link.getToolParameterId(), skillToolParameterRepository::countSkillsByToolParameterIds,
                skillMapper::toPartEmbeddedResponse));
        // One path is used once across the five kinds, so ordering by path gives the directory listing.
        parts.sort(Comparator.comparing(SkillPartEmbeddedResponse::path));

        return skillMapper.toDetailResponse(skill, blob.getSize(), new String(blob.getContent(), StandardCharsets.UTF_8),
                parts);
    }

    @Override
    public Skill getEntityById(UUID organizationId, UUID skillId) {
        // Looked up within the organization, so a skill of another organization answers the same
        // 404 as an unknown id.
        return skillRepository.findByIdAndOrganizationId(skillId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SkillServiceLogConstant.SKILL_NOT_FOUND, skillId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(SkillConstant.FIELD_SKILL_ID, skillId.toString()));
        });
    }

    @Override
    public List<Skill> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SkillServiceLogConstant.LOADING_VERSION_SKILLS, versionId);
        return skillRepository.findByOrganizationIdAndVersionIdOrderByNameAsc(organizationId, versionId);
    }

    @Override
    public int countByVersionIds(UUID organizationId, Collection<UUID> versionIds) {
        if (versionIds.isEmpty()) {
            return 0;
        }
        return Math.toIntExact(skillRepository.countByOrganizationIdAndVersionIdIn(organizationId, versionIds));
    }

    @Override
    public Map<SkillPartKind, Integer> countPartLinksByVersionIds(UUID organizationId, Collection<UUID> versionIds) {
        Map<SkillPartKind, Integer> counts = new EnumMap<>(SkillPartKind.class);
        for (SkillPartKind kind : SkillPartKind.values()) {
            counts.put(kind, 0);
        }
        if (versionIds.isEmpty()) {
            return counts;
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SkillServiceLogConstant.COUNTING_VERSION_SKILLS, versionIds.size(),
                organizationId);
        for (SkillPartCountProjection row : skillRepository.countPartLinksByVersionIds(organizationId, versionIds)) {
            counts.put(SkillPartKind.valueOf(row.getKind()), Math.toIntExact(row.getLinks()));
        }
        return counts;
    }

    @Override
    public List<SkillPartLink> getPartLinks(UUID organizationId, UUID versionId, String skillName) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SkillServiceLogConstant.LOADING_PART_LINKS, skillName, versionId);
        Optional<Skill> skill = skillRepository.findByOrganizationIdAndVersionIdAndName(organizationId, versionId, skillName);
        if (skill.isEmpty()) {
            return List.of();
        }
        UUID skillId = skill.get().getId();
        List<SkillPartLink> links = new ArrayList<>();
        skillWorkflowRepository.findBySkill_IdOrderByPathAsc(skillId).forEach(link -> links.add(skillMapper.toPartLink(link)));
        skillScriptRepository.findBySkill_IdOrderByPathAsc(skillId).forEach(link -> links.add(skillMapper.toPartLink(link)));
        skillConventionRepository.findBySkill_IdOrderByPathAsc(skillId).forEach(link -> links.add(skillMapper.toPartLink(link)));
        skillReferenceRepository.findBySkill_IdOrderByPathAsc(skillId).forEach(link -> links.add(skillMapper.toPartLink(link)));
        skillToolParameterRepository.findBySkill_IdOrderByPathAsc(skillId)
                .forEach(link -> links.add(skillMapper.toPartLink(link)));
        return links;
    }

    @Override
    public List<SkillPartLink> getLinksToPart(UUID organizationId, SkillPartKind kind, UUID partId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, SkillServiceLogConstant.LOADING_LINKS_TO_PART, kind, partId);
        List<SkillPartLink> links = switch (kind) {
            case WORKFLOW -> skillWorkflowRepository.findByWorkflowIdAndSkill_OrganizationId(partId, organizationId).stream()
                    .map(skillMapper::toPartLink).toList();
            case SCRIPT -> skillScriptRepository.findByScriptIdAndSkill_OrganizationId(partId, organizationId).stream()
                    .map(skillMapper::toPartLink).toList();
            case CONVENTION -> skillConventionRepository.findByConventionIdAndSkill_OrganizationId(partId, organizationId)
                    .stream().map(skillMapper::toPartLink).toList();
            case REFERENCE -> skillReferenceRepository.findByReferenceIdAndSkill_OrganizationId(partId, organizationId)
                    .stream().map(skillMapper::toPartLink).toList();
            case TOOL_PARAMETER -> skillToolParameterRepository
                    .findByToolParameterIdAndSkill_OrganizationId(partId, organizationId).stream()
                    .map(skillMapper::toPartLink).toList();
        };
        return links.stream().sorted(Comparator.comparing(SkillPartLink::skillName)).toList();
    }

    // The links of one kind with the number of skills that share each part, counted in one query.
    private <L> List<SkillPartEmbeddedResponse> parts(List<L> links, Function<L, UUID> partId,
            Function<Collection<UUID>, List<SkillPartUsageProjection>> countSkills,
            BiFunction<L, Integer, SkillPartEmbeddedResponse> toResponse) {
        if (links.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> usage = countSkills.apply(links.stream().map(partId).toList()).stream()
                .collect(Collectors.toMap(SkillPartUsageProjection::getPartId, SkillPartUsageProjection::getSkills));
        return links.stream()
                .map(link -> toResponse.apply(link, usage.getOrDefault(partId.apply(link), 0L).intValue()))
                .toList();
    }
}
