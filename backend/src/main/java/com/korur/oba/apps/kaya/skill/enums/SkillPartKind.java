package com.korur.oba.apps.kaya.skill.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The kind of a file a skill links inside its directory, one per link table of the skill domain.
 *
 * - WORKFLOW: a {@link com.korur.oba.apps.kaya.workflow.entity.Workflow}, from workflows/ of the skill.
 * - SCRIPT: a {@link com.korur.oba.apps.kaya.script.entity.Script}, from scripts/ or any executable file.
 * - CONVENTION: a {@link com.korur.oba.apps.kaya.convention.entity.Convention}, from rules/ or conventions/.
 * - REFERENCE: a {@link com.korur.oba.apps.kaya.reference.entity.Reference}, any other documentation or asset.
 * - TOOL_PARAMETER: a {@link com.korur.oba.apps.kaya.toolparameter.entity.ToolParameter}, a JSON or YAML
 *   file outside those folders.
 */
public enum SkillPartKind {
    WORKFLOW,
    SCRIPT,
    CONVENTION,
    REFERENCE,
    TOOL_PARAMETER
}
