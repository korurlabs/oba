package com.korur.oba.apps.kaya.skill.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The number of skills that link one part.
 */
public interface SkillPartUsageProjection {

    UUID getPartId();

    Long getSkills();
}
