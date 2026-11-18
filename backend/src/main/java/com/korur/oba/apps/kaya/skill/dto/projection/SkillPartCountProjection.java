package com.korur.oba.apps.kaya.skill.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The number of links of one part kind across the skills of some versions, so a page can count the
 * files a kind delivers without loading the links.
 */
public interface SkillPartCountProjection {

    // The name of a com.korur.oba.apps.kaya.skill.enums.SkillPartKind constant.
    String getKind();

    Long getLinks();
}
