package com.korur.oba.apps.kaya.session.dto.derived;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * What the events of a session say about its subagents and the skills it used.
 *
 * @param subagents every subagent whose id is known, in start order
 * @param skillUses the use totals per skill and source, in first use order
 */
public record SessionActivity(
        List<DerivedSubagent> subagents,
        List<DerivedSkillUse> skillUses
) {
}
