package com.korur.oba.apps.kaya.rule.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.rule.constant.service.RuleServiceLogConstant;
import com.korur.oba.apps.kaya.rule.dto.embedded.RuleIndex;
import com.korur.oba.apps.kaya.rule.entity.Rule;
import com.korur.oba.apps.kaya.rule.entity.RuleConvention;
import com.korur.oba.apps.kaya.rule.repository.RuleRepository;
import com.korur.oba.apps.kaya.rule.service.RuleService;

/**
 * The {@link RuleService} implementation.
 */
@Service
@RequiredArgsConstructor
public class RuleServiceImpl implements RuleService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RuleServiceImpl.class);

    private final RuleRepository ruleRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public List<Rule> replaceVersion(UUID organizationId, UUID versionId, List<RuleIndex> rules) {
        // The old rows are deleted and flushed first, so a rule saved again at the same path does not
        // collide with the old row on the version and path unique key.
        int removed = ruleRepository.deleteByVersion(organizationId, versionId);
        List<Rule> entities = rules.stream()
                .map(index -> toEntity(organizationId, versionId, index))
                .toList();
        List<Rule> saved = ruleRepository.saveAll(entities);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RuleServiceLogConstant.RULES_REPLACED,
                versionId, organizationId, removed, saved.size());
        return saved;
    }

    @Override
    @Transactional
    public Rule saveFile(UUID organizationId, UUID versionId, RuleIndex index) {
        // The old row is deleted and flushed first, so the new row does not collide with it on the
        // version and path unique key.
        int removed = ruleRepository.deleteByPath(organizationId, versionId, index.relativePath());
        Rule saved = ruleRepository.save(toEntity(organizationId, versionId, index));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RuleServiceLogConstant.RULE_SAVED,
                index.relativePath(), versionId, organizationId, removed);
        return saved;
    }

    @Override
    @Transactional
    public boolean deleteFile(UUID organizationId, UUID versionId, String relativePath) {
        int removed = ruleRepository.deleteByPath(organizationId, versionId, relativePath);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RuleServiceLogConstant.RULE_DELETED,
                relativePath, versionId, organizationId, removed);
        return removed > 0;
    }

    private Rule toEntity(UUID organizationId, UUID versionId, RuleIndex index) {
        List<String> paths = index.paths() == null ? new ArrayList<>() : new ArrayList<>(index.paths());
        Rule rule = Rule.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(index.contentBlobId())
                .relativePath(index.relativePath())
                .description(index.description())
                .paths(paths)
                // Derived rather than taken from the caller, so it always agrees with the paths list
                // as the database check requires.
                .alwaysOn(paths.isEmpty())
                .overridesPath(index.overridesPath())
                .build();
        if (index.conventionPaths() != null) {
            index.conventionPaths().stream()
                    .distinct()
                    .map(conventionPath -> RuleConvention.builder().rule(rule).conventionPath(conventionPath).build())
                    .forEach(rule.getConventions()::add);
        }
        return rule;
    }
}
