package com.korur.oba.apps.kaya.rule.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.rule.entity.RuleConvention;

/**
 * Reads the {@link RuleConvention} links of the rule domain; they are written with their rule.
 */
@Repository
public interface RuleConventionRepository
        extends JpaRepository<RuleConvention, UUID>, JpaSpecificationExecutor<RuleConvention> {

    List<RuleConvention> findByRule_IdInOrderByConventionPathAsc(Collection<UUID> ruleIds);
}
