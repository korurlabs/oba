package com.korur.oba.apps.kaya.link.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.link.dto.request.LinkQueryRequest;
import com.korur.oba.apps.kaya.link.dto.response.DependencyResponse;
import com.korur.oba.apps.kaya.link.dto.response.LinkResponse;

/**
 * Reads the links between Markdown files, each FOUND when the scope delivers its target and
 * BROKEN otherwise. Files come from {@link com.korur.oba.apps.kaya.version.service.VersionQueryService}
 * for a version and from {@link com.korur.oba.apps.kaya.release.service.ReleaseQueryService} for a
 * repository; nothing is stored.
 */
public interface LinkQueryService {

    /**
     * Lists the links of a scope: with versionId alone, those within the version's own files; with
     * repositoryId alone, those its latest release delivers, none while it has no release; with
     * both, those the repository would deliver with the version, a DRAFT or IN_REVIEW one, in place
     * of its owner's current version.
     *
     * @param organizationId the organization of the request
     * @param request        the version, the repository, or both
     * @return the links by source path and line
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version or repository
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException GEN_VAL_001 when
     *         neither id is given, or with both when the version is not open or its owner is not
     *         composed by the repository
     */
    List<LinkResponse> getLinks(UUID organizationId, LinkQueryRequest request);

    /**
     * The dependencies of one file of a scope made of several versions: what it links to (its
     * Markdown links, a rule's conventions, a skill's parts), each FOUND or BROKEN against the files
     * of the scope, and what uses it (Markdown links to it, rules citing it, skills sharing it). A
     * path two versions deliver is read from the first of them.
     *
     * @param organizationId the organization of the request
     * @param versionIds     the versions of the scope; an id the organization does not have is left
     *                       out
     * @param path           the path of the file relative to the repository root
     * @return the dependencies, each list by relation and path
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when no
     *         version of the scope delivers the path
     */
    DependencyResponse getDependencies(UUID organizationId, Collection<UUID> versionIds, String path);
}
