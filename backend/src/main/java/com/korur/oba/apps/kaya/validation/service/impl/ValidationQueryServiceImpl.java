package com.korur.oba.apps.kaya.validation.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.validation.constant.service.ValidationServiceLogConstant;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;
import com.korur.oba.apps.kaya.validation.helper.CompositionCandidate;
import com.korur.oba.apps.kaya.validation.helper.CompositionRules;
import com.korur.oba.apps.kaya.validation.helper.FileCandidate;
import com.korur.oba.apps.kaya.validation.helper.FileRules;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;

/**
 * The {@link ValidationQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ValidationQueryServiceImpl implements ValidationQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ValidationQueryServiceImpl.class);

    private final FileRules fileRules;
    private final CompositionRules compositionRules;
    private final LoggingService loggingService;

    @Override
    public List<Violation> validateFiles(List<FileCandidate> files) {
        List<Violation> violations = new ArrayList<>();
        for (FileCandidate file : files) {
            violations.addAll(validateFile(file));
        }
        violations.addAll(validateLinks(files));
        return violations;
    }

    @Override
    public List<Violation> validateFile(FileCandidate file) {
        return fileRules.validate(file);
    }

    @Override
    public List<Violation> validateFileChange(FileCandidate file, FileCandidate before) {
        List<Violation> violations = validateFile(file);
        boolean comparable = before != null && before.content() != null && file.content() != null;
        if (!comparable || violations.stream().noneMatch(violation -> violation.rule().isSizeLimit())) {
            return violations;
        }
        Set<ViolationRule> brokenBefore = validateFile(before).stream()
                .map(Violation::rule)
                .filter(ViolationRule::isSizeLimit)
                .collect(Collectors.toSet());
        return violations.stream()
                .filter(violation -> !(violation.rule().isSizeLimit() && brokenBefore.contains(violation.rule())
                        && fileRules.measure(file, violation.rule()) <= fileRules.measure(before, violation.rule())))
                .toList();
    }

    @Override
    public List<Violation> validateLinks(List<FileCandidate> files) {
        return compositionRules.validateLinks(files);
    }

    @Override
    public List<Violation> validateComposition(CompositionCandidate composition) {
        return compositionRules.validate(composition);
    }

    @Override
    public void requireValid(List<Violation> violations) {
        if (violations.isEmpty()) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ValidationServiceLogConstant.VIOLATIONS_REFUSED, violations.size(),
                violations.stream().map(Violation::rule).collect(Collectors.toCollection(HashSet::new)));
        Map<String, Object> errorValues = Map.of(ValidationConstant.FIELD_VIOLATIONS, violations);
        boolean twoOwners = violations.stream().anyMatch(violation -> violation.rule() == ViolationRule.PATH_IN_TWO_SETS);
        if (twoOwners) {
            throw new ConflictException(ExceptionDomainKayaError.RELEASE_PATH_IN_TWO_OWNERS, errorValues);
        }
        // An unscoped mount rule and a locked item each have their own code, so a client can name
        // the rule and folder, or the locked item, without reading the list.
        Optional<Violation> unscoped = firstOf(violations, ViolationRule.MOUNT_RULE_SCOPE);
        if (unscoped.isPresent()) {
            Map<String, Object> values = new LinkedHashMap<>(errorValues);
            values.put(ValidationConstant.FIELD_RULE, unscoped.get().path());
            values.put(ValidationConstant.FIELD_FOLDER, unscoped.get().rootPath());
            values.put(ValidationConstant.FIELD_PACK, firstOwner(unscoped.get()));
            throw new ValidationException(ExceptionDomainKayaError.MOUNT_RULE_UNSCOPED, values);
        }
        Optional<Violation> locked = firstOf(violations, ViolationRule.OVERRIDE_LOCKED);
        if (locked.isPresent()) {
            Map<String, Object> values = new LinkedHashMap<>(errorValues);
            values.put(ValidationConstant.FIELD_PATH, locked.get().path());
            values.put(ValidationConstant.FIELD_PACK, firstOwner(locked.get()));
            throw new ValidationException(ExceptionDomainKayaError.ITEM_LOCKED, values);
        }
        throw new ValidationException(ExceptionDomainKayaError.VALIDATION_RULES_BROKEN, errorValues);
    }

    private static Optional<Violation> firstOf(List<Violation> violations, ViolationRule rule) {
        return violations.stream().filter(violation -> violation.rule() == rule).findFirst();
    }

    /** The pack a violation names first as text, or an empty text when it names none. */
    private static String firstOwner(Violation violation) {
        return violation.ownerIds() == null || violation.ownerIds().isEmpty() ? "" : violation.ownerIds().get(0).toString();
    }
}
