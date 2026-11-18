package com.korur.oba.apps.kaya.rule.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.rule.constant.RuleConstant;
import com.korur.oba.apps.kaya.rule.constant.service.RuleServiceLogConstant;
import com.korur.oba.apps.kaya.rule.dto.request.RuleQueryRequest;
import com.korur.oba.apps.kaya.rule.dto.response.RuleDetailResponse;
import com.korur.oba.apps.kaya.rule.dto.response.RuleListResponse;
import com.korur.oba.apps.kaya.rule.entity.Rule;
import com.korur.oba.apps.kaya.rule.entity.RuleConvention;
import com.korur.oba.apps.kaya.rule.mapper.RuleMapper;
import com.korur.oba.apps.kaya.rule.repository.RuleConventionRepository;
import com.korur.oba.apps.kaya.rule.repository.RuleRepository;
import com.korur.oba.apps.kaya.rule.service.RuleQueryService;
import com.korur.oba.apps.kaya.rule.specification.RuleSpecification;

/**
 * The {@link RuleQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleQueryServiceImpl implements RuleQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RuleQueryServiceImpl.class);

    // Version first, so the rules of one version stay together when a page lists several.
    private static final Sort LIST_SORT = Sort.by(RuleConstant.FIELD_VERSION_ID, RuleConstant.FIELD_RELATIVE_PATH,
            RuleConstant.FIELD_ID);

    private final RuleRepository ruleRepository;
    private final RuleConventionRepository ruleConventionRepository;
    private final BlobQueryService blobQueryService;
    private final RuleMapper ruleMapper;
    private final LoggingService loggingService;

    @Override
    public List<RuleListResponse> getRules(UUID organizationId, RuleQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RuleServiceLogConstant.LISTING_RULES, organizationId, request);
        List<Rule> rules = ruleRepository.findAll(RuleSpecification.filterBy(organizationId, request), LIST_SORT);
        if (rules.isEmpty()) {
            return List.of();
        }

        // The links and the sizes are read in one query each, not once per rule.
        List<UUID> ruleIds = rules.stream().map(Rule::getId).toList();
        Map<UUID, List<RuleConvention>> conventionsByRule = ruleConventionRepository
                .findByRule_IdInOrderByConventionPathAsc(ruleIds).stream()
                .collect(Collectors.groupingBy(link -> link.getRule().getId()));
        Map<UUID, Integer> sizes = blobQueryService.getSizesByIds(organizationId,
                rules.stream().map(Rule::getContentBlobId).collect(Collectors.toSet()));

        return rules.stream()
                .map(rule -> ruleMapper.toListResponse(rule,
                        conventionsByRule.getOrDefault(rule.getId(), List.of()),
                        sizes.get(rule.getContentBlobId())))
                .toList();
    }

    @Override
    public RuleDetailResponse getRule(UUID organizationId, UUID ruleId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RuleServiceLogConstant.READING_RULE, ruleId, organizationId);
        Rule rule = getEntityById(organizationId, ruleId);
        Blob blob = blobQueryService.getEntityById(organizationId, rule.getContentBlobId());
        List<RuleConvention> conventions = ruleConventionRepository.findByRule_IdInOrderByConventionPathAsc(
                List.of(rule.getId()));
        return ruleMapper.toDetailResponse(rule, conventions, blob.getSize(),
                new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    @Override
    public Rule getEntityById(UUID organizationId, UUID ruleId) {
        // Looked up within the organization, so a rule of another organization answers the same
        // 404 as an unknown id.
        return ruleRepository.findByIdAndOrganizationId(ruleId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, RuleServiceLogConstant.RULE_NOT_FOUND, ruleId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(RuleConstant.FIELD_RULE_ID, ruleId.toString()));
        });
    }

    @Override
    public List<Rule> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RuleServiceLogConstant.LOADING_VERSION_RULES, versionId);
        return ruleRepository.findByOrganizationIdAndVersionIdOrderByRelativePathAsc(organizationId, versionId);
    }

    @Override
    public List<Rule> getEntitiesCitingConvention(UUID organizationId, Collection<UUID> versionIds, String conventionPath) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RuleServiceLogConstant.LOADING_CITING_RULES, conventionPath,
                versionIds.size());
        if (versionIds.isEmpty()) {
            return List.of();
        }
        return ruleRepository.findCitingConvention(organizationId, versionIds, conventionPath);
    }
}
