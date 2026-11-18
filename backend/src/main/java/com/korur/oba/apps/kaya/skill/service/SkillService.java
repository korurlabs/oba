package com.korur.oba.apps.kaya.skill.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillIndex;
import com.korur.oba.apps.kaya.skill.entity.Skill;

/**
 * Writes the {@link Skill}s of a version and their part links from the index the parse extracted. It
 * parses nothing and stores no part: it checks that every linked part is one of the version's through
 * {@link com.korur.oba.apps.kaya.workflow.service.WorkflowQueryService},
 * {@link com.korur.oba.apps.kaya.script.service.ScriptQueryService},
 * {@link com.korur.oba.apps.kaya.convention.service.ConventionQueryService},
 * {@link com.korur.oba.apps.kaya.reference.service.ReferenceQueryService} and
 * {@link com.korur.oba.apps.kaya.toolparameter.service.ToolParameterQueryService}.
 *
 * Called only by the render domain, inside the transaction that stores a version's files, after it has
 * replaced the version's parts, because replacing a part removes the links to it.
 */
public interface SkillService {

    /**
     * Replaces every skill of a version with the given ones, part links included.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param skills         the index of each skill directory of the version; may be empty
     * @return the skills written, in the given order
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when a link
     *         names a part that is not one of the version's, so a skill never reaches into another version
     */
    List<Skill> replaceVersion(UUID organizationId, UUID versionId, List<SkillIndex> skills);

    /**
     * Writes one skill of a version, with its part links: the skill of the same name is replaced, or the
     * skill is added when the version has none of that name. Every part it links must belong to the
     * version; replacing a skill never deletes a part, so a part another skill links stays.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param skill          the index of the skill, with its links
     * @return the skill written
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException NOT_VERSIONED_RESOURCE_FOUND
     *         when a link names a part of another version or none
     */
    Skill saveSkill(UUID organizationId, UUID versionId, SkillIndex skill);

    /**
     * Deletes one skill of a version; the database removes its part links with it, and the parts stay.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param name           the name of the skill
     * @return whether the version had a skill of that name
     */
    boolean deleteSkill(UUID organizationId, UUID versionId, String name);
}
