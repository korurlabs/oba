package com.korur.oba.apps.kaya.render.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.toolparameter.enums.ToolParameterFormat;

/**
 * One file of a skill directory other than its SKILL.md, mapped to the part kind that stores it by
 * {@link com.korur.oba.apps.kaya.render.helper.SkillFolderParser}.
 *
 * @param kind       the part kind
 * @param path       the path inside the skill directory, kept as the link path so links in SKILL.md
 *                   still resolve
 * @param name       the file name, the last segment of the path
 * @param content    the exact bytes
 * @param executable whether a {@link SkillPartKind#SCRIPT} has the executable bit
 * @param title      the title of a {@link SkillPartKind#WORKFLOW} or {@link SkillPartKind#CONVENTION};
 *                   null otherwise or when it has none
 * @param mediaType  the media type of a {@link SkillPartKind#REFERENCE}; null otherwise
 * @param format     the format of a {@link SkillPartKind#TOOL_PARAMETER}; null otherwise
 */
public record SkillPartFile(
        SkillPartKind kind,
        String path,
        String name,
        byte[] content,
        boolean executable,
        String title,
        String mediaType,
        ToolParameterFormat format
) {
}
