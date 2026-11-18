package com.korur.oba.apps.kaya.skill.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillPartLink;
import com.korur.oba.apps.kaya.skill.dto.request.SkillQueryRequest;
import com.korur.oba.apps.kaya.skill.dto.response.SkillDetailResponse;
import com.korur.oba.apps.kaya.skill.dto.response.SkillListResponse;
import com.korur.oba.apps.kaya.skill.entity.Skill;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;

/**
 * Reads the {@link Skill}s of an organization's versions with their part links
 * ({@link com.korur.oba.apps.kaya.skill.entity.SkillWorkflow}, {@link com.korur.oba.apps.kaya.skill.entity.SkillScript},
 * {@link com.korur.oba.apps.kaya.skill.entity.SkillConvention}, {@link com.korur.oba.apps.kaya.skill.entity.SkillReference},
 * {@link com.korur.oba.apps.kaya.skill.entity.SkillToolParameter}), and the size and content of SKILL.md
 * through {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.skill.controller.SkillController} for the Skills page, and by the
 * render and release domains to load the skills of a version.
 */
public interface SkillQueryService {

    /**
     * Lists the skills of the given versions, ordered by version, name and id. A version id of another
     * organization matches nothing.
     *
     * @param organizationId the organization of the request
     * @param request        the 1 to 20 version ids
     * @return the skills with the byte sizes of their SKILL.md, without content or parts
     */
    List<SkillListResponse> getSkills(UUID organizationId, SkillQueryRequest request);

    /**
     * Reads one skill with its whole SKILL.md as UTF-8 text and its parts ordered by path, each with the
     * number of skills that link it.
     *
     * @param organizationId the organization of the request
     * @param skillId        the skill
     * @return the skill with its content and parts
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such skill, or its blob is missing
     */
    SkillDetailResponse getSkill(UUID organizationId, UUID skillId);

    /**
     * Loads a skill of the organization.
     *
     * A skill of another organization answers the same 404 as an unknown id, so its existence is not
     * revealed.
     *
     * @param organizationId the organization of the request
     * @param skillId        the skill
     * @return the skill
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such skill
     */
    Skill getEntityById(UUID organizationId, UUID skillId);

    /**
     * Loads every skill of one version of the organization, ordered by name.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the skills; empty when the version has none or belongs to another organization
     */
    List<Skill> getEntitiesByVersionId(UUID organizationId, UUID versionId);

    /**
     * Counts the skills of the given versions of the organization, without loading them.
     *
     * @param organizationId the organization of the versions
     * @param versionIds     the versions; ids of another organization match nothing
     * @return the number of skills; 0 when the ids are empty
     */
    int countByVersionIds(UUID organizationId, Collection<UUID> versionIds);

    /**
     * Counts the part links of the skills of the given versions per part kind, in one query, so a
     * count of the files the skills deliver costs no query per skill. A part that two skills share
     * counts once per skill, as each skill delivers its own copy.
     *
     * @param organizationId the organization of the versions
     * @param versionIds     the versions; ids of another organization match nothing
     * @return a count for every {@link SkillPartKind}, 0 for a kind no skill links; all 0 when the
     *         ids are empty
     */
    Map<SkillPartKind, Integer> countPartLinksByVersionIds(UUID organizationId, Collection<UUID> versionIds);

    /**
     * The links of one skill to its parts, so a page can list what the skill is made of.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param skillName      the skill's directory name
     * @return every {@link SkillPartLink} of the skill, by kind and path; empty when the version
     *         has no such skill or belongs to another organization
     */
    List<SkillPartLink> getPartLinks(UUID organizationId, UUID versionId, String skillName);

    /**
     * Every link to one part, one per skill that shares it, so a page can name the skills a change
     * to the part reaches.
     *
     * @param organizationId the organization of the part
     * @param kind           the kind of the part
     * @param partId         the part row of that kind
     * @return every {@link SkillPartLink} to the part, by skill name; empty when no skill links it
     */
    List<SkillPartLink> getLinksToPart(UUID organizationId, SkillPartKind kind, UUID partId);
}
