package com.korur.oba.apps.kaya.version.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.dto.QueryRequest;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.version.dto.embedded.FileHistoryEntry;
import com.korur.oba.apps.kaya.version.dto.request.VersionQueryRequest;
import com.korur.oba.apps.kaya.version.dto.response.FileDiffResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionDetailResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionFileContentResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionOrder;

/**
 * Reads the {@link Version}s of an organization and the files they render: the history of a pack, one
 * version with its file list, one file with its content, and how two versions differ.
 * Files are never read from a table of this domain; every file list is the output of
 * {@link com.korur.oba.apps.kaya.render.service.RenderQueryService}, so what a member reads is what a
 * release would deliver.
 *
 * Called by {@link com.korur.oba.apps.kaya.version.controller.VersionController},
 * {@link com.korur.oba.apps.kaya.version.controller.DraftController}, the version command service,
 * and by the review, release, link and delivery domains to load the versions they work on.
 */
public interface VersionQueryService {

    /**
     * Lists the versions of the organization, newest number first, optionally of one pack or
     * one status.
     *
     * @param organizationId the organization of the request
     * @param request        the paging and the filters
     * @return one page of versions
     */
    Page<VersionResponse> getVersions(UUID organizationId, VersionQueryRequest request);

    /**
     * Lists the versions of the organization in the given order, optionally of one pack or
     * one status. The version id breaks ties, so pages stay stable.
     *
     * @param organizationId the organization of the request
     * @param request        the paging and the filters
     * @param order          the order of the list
     * @return one page of versions
     */
    Page<VersionResponse> getVersions(UUID organizationId, VersionQueryRequest request, VersionOrder order);

    /**
     * Reads one version with its rendered file list.
     *
     * @param organizationId the organization of the request
     * @param versionId      the version
     * @return the version and its files by path, without content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version
     */
    VersionDetailResponse getVersion(UUID organizationId, UUID versionId);

    /**
     * Reads one rendered file of a version with its content: as UTF_8 text when the bytes are valid
     * UTF-8, as BASE64 otherwise; a link has its target and no content.
     *
     * @param organizationId the organization of the request
     * @param versionId      the version
     * @param path           the path of the file
     * @return the file
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version or the version has no file at the path
     */
    VersionFileContentResponse getFileContent(UUID organizationId, UUID versionId, String path);

    /**
     * The changed paths of a version against another, by path code point by code point, each with
     * a unified diff of its text.
     *
     * @param organizationId   the organization of the request
     * @param versionId        the version
     * @param againstVersionId the version to compare with; null for the version's base, and a
     *                         version without a base is compared with no files
     * @param path             the one path to compare, for the compare view of one document; null
     *                         for every path
     * @return one entry per added, modified or deleted path; for a path, at most its own entry
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version
     */
    List<FileDiffResponse> getDiff(UUID organizationId, UUID versionId, UUID againstVersionId, String path);

    /**
     * The history of the file at one path: the versions of the owner of a version that added,
     * changed or deleted that file against their base, newest number first. The owner's released
     * versions, its version in review and the caller's own drafts are read; another member's draft
     * is not. Each page reads amount versions of the owner, renders the path in each and in its
     * base, and keeps the versions where the two differ, so a page may list fewer entries than
     * amount while its totals count the versions read.
     *
     * @param user           the caller, whose own drafts are included
     * @param organizationId the organization of the request
     * @param versionId      any version of the pack
     * @param path           the path of the file relative to the repository root
     * @param page           the paging over the owner's versions
     * @return one page of entries, each a {@link FileHistoryEntry}
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version
     */
    Page<FileHistoryEntry> getFileHistory(User user, UUID organizationId, UUID versionId, String path, QueryRequest page);

    /**
     * Loads a version of the organization.
     *
     * A version of another organization answers the same 404 as an unknown id, so its existence is
     * not revealed.
     *
     * @param organizationId the organization of the request
     * @param versionId      the version
     * @return the version
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version
     */
    Version getEntityById(UUID organizationId, UUID versionId);

    /**
     * Loads several versions of the organization, for a page that reads one scope made of several
     * packs.
     *
     * @param organizationId the organization of the request
     * @param versionIds     the versions; an id the organization does not have is left out, so
     *                       another organization's version is neither read nor revealed
     * @return the versions found, in the order of the ids, each once
     */
    List<Version> getEntitiesByIds(UUID organizationId, Collection<UUID> versionIds);

    /**
     * Loads every version of a pack, whatever its status, so a caller can tell whether the pack
     * has ever been submitted, approved or released.
     *
     * @param organizationId the organization of the pack
     * @param packId         the pack; a pack of another organization has no versions here
     * @return the versions, lowest number first; empty for a pack without versions
     */
    List<Version> getEntitiesOfPack(UUID organizationId, UUID packId);

    /**
     * Renders the files of a version at the repository paths its pack stores them.
     *
     * @param organizationId the organization of the version
     * @param version        the version; null for no files
     * @return every {@link RenderedFile} of the version, sorted by path; empty for null
     */
    List<RenderedFile> render(UUID organizationId, Version version);
}
