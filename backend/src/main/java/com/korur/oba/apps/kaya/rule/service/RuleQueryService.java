package com.korur.oba.apps.kaya.rule.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.rule.dto.request.RuleQueryRequest;
import com.korur.oba.apps.kaya.rule.dto.response.RuleDetailResponse;
import com.korur.oba.apps.kaya.rule.dto.response.RuleListResponse;
import com.korur.oba.apps.kaya.rule.entity.Rule;

/**
 * Reads the {@link Rule}s of an organization's versions with their
 * {@link com.korur.oba.apps.kaya.rule.entity.RuleConvention} links, and their sizes and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.rule.controller.RuleController} for the Rules page, and by the
 * render and release domains to load the rules of a version.
 */
public interface RuleQueryService {

    /**
     * Lists the rules of the given versions, ordered by version, path and id. A version id of another
     * organization matches nothing.
     *
     * @param organizationId the organization of the request
     * @param request        the 1 to 20 version ids
     * @return the rules with their convention links and byte sizes, without content
     */
    List<RuleListResponse> getRules(UUID organizationId, RuleQueryRequest request);

    /**
     * Reads one rule with its whole file as UTF-8 text.
     *
     * @param organizationId the organization of the request
     * @param ruleId         the rule
     * @return the rule with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such rule, or its blob is missing
     */
    RuleDetailResponse getRule(UUID organizationId, UUID ruleId);

    /**
     * Loads a rule of the organization.
     *
     * A rule of another organization answers the same 404 as an unknown id, so its existence is not
     * revealed.
     *
     * @param organizationId the organization of the request
     * @param ruleId         the rule
     * @return the rule
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such rule
     */
    Rule getEntityById(UUID organizationId, UUID ruleId);

    /**
     * Loads every rule of one version of the organization, ordered by path.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the rules; empty when the version has none or belongs to another organization
     */
    List<Rule> getEntitiesByVersionId(UUID organizationId, UUID versionId);

    /**
     * Loads the rules of some versions of the organization that cite a convention, so a page can
     * show which rules a change to that convention reaches.
     *
     * @param organizationId the organization of the versions
     * @param versionIds     the versions; ids of another organization match nothing
     * @param conventionPath the convention path relative to the repository root, as
     *                       docs/private/conventions/backend/logging.md
     * @return the citing rules, by version and path; empty for no versions
     */
    List<Rule> getEntitiesCitingConvention(UUID organizationId, Collection<UUID> versionIds, String conventionPath);
}
