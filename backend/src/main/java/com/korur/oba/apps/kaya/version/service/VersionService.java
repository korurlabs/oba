package com.korur.oba.apps.kaya.version.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.version.dto.embedded.DeliveredFile;
import com.korur.oba.apps.kaya.version.dto.request.DraftCreateRequest;
import com.korur.oba.apps.kaya.version.dto.request.DraftFileSaveRequest;
import com.korur.oba.apps.kaya.version.dto.request.DraftRebaseRequest;
import com.korur.oba.apps.kaya.version.dto.request.ItemOverrideRequest;
import com.korur.oba.apps.kaya.version.dto.request.ProposalFileRequest;
import com.korur.oba.apps.kaya.version.dto.response.DraftFileSaveResponse;
import com.korur.oba.apps.kaya.version.dto.response.ItemOverrideResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.entity.Version;

/**
 * Writes the {@link Version}s of a pack. A member starts a draft from the owner's
 * current version, edits it file by file and rebases it when the owner moves on; the review domain
 * submits it, approves it or hands it back. Only a draft's proposer writes to it, and only while it
 * is a DRAFT.
 *
 * Every new version is filled by one code path: the files of its source version are rendered by
 * {@link com.korur.oba.apps.kaya.render.service.RenderQueryService} and parsed into the new version by
 * {@link com.korur.oba.apps.kaya.render.service.RenderService}, which also checks every file. The
 * current-version pointer of the owner always names its newest approved version; there is no
 * pinning, and only {@link #approve} and {@link #seed} move it, through
 * {@link com.korur.oba.apps.kaya.pack.service.PackService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.version.controller.DraftController},
 * {@link com.korur.oba.apps.kaya.version.controller.VersionController}, and by the review and
 * delivery domains.
 */
public interface VersionService {

    /**
     * Starts a draft as a copy of the owner's current version, with the next version number.
     *
     * @param user           the proposer
     * @param organizationId the organization of the request
     * @param request        the pack to draft
     * @return the draft
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such pack
     */
    VersionResponse createDraft(User user, UUID organizationId, DraftCreateRequest request);

    /**
     * Adds or replaces one file of the proposer's draft in one transaction. A file that does not
     * parse, breaks a file or link rule, or starts an override marker is refused,
     * and the draft keeps its files as they were.
     *
     * @param user           the proposer
     * @param organizationId the organization of the request
     * @param draftId        the draft
     * @param request        the file; its executable flag is taken only for a hook or a file of a
     *                       skill directory other than SKILL.md
     * @return the {@link DraftFileSaveResponse}: the entity the file is stored as, its hash and size
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_027
     *         INVALID_FILE or KAY_CFG_028 NOT_DELIVERABLE with the path and the reason (INVALID_FILE
     *         also for an executable file of another kind), KAY_CFG_001
     *         for an override marker the file did not carry, GEN_VAL_001 for content that is not
     *         base64
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException GEN_SEC_002 when the
     *         user is not the proposer
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_014 when the
     *         version is not a draft
     */
    DraftFileSaveResponse saveFile(User user, UUID organizationId, UUID draftId, DraftFileSaveRequest request);

    /**
     * Removes one file of the proposer's draft.
     *
     * @param user           the proposer
     * @param organizationId the organization of the request
     * @param draftId        the draft
     * @param path           the path of the file
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         version or the file at the path does not exist
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException GEN_SEC_002 when the
     *         user is not the proposer
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_014 when the
     *         version is not a draft
     */
    void deleteFile(User user, UUID organizationId, UUID draftId, String path);

    /**
     * Discards a draft with its files. A draft that a reviewer has decided on keeps the record of
     * that review, so it is not deleted.
     *
     * @param user           the proposer
     * @param organizationId the organization of the request
     * @param draftId        the draft
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_014 when the
     *         version is not a draft, KAY_CFG_020 when it was reviewed
     */
    void deleteDraft(User user, UUID organizationId, UUID draftId);

    /**
     * Moves a draft onto the owner's current version: every path only the current version changed
     * takes its state, every path only the draft changed keeps the draft's, and a path both changed
     * needs a resolution in the request.
     *
     * @param user           the proposer
     * @param organizationId the organization of the request
     * @param draftId        the draft
     * @param request        the resolutions; null for none
     * @return the rebased draft, or the draft unchanged when it already rests on the current version
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_002 with the
     *         unresolved conflicts, KAY_CFG_014 when the version is not a draft
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException GEN_VAL_001 when a
     *         path is both kept and taken
     */
    VersionResponse rebase(User user, UUID organizationId, UUID draftId, DraftRebaseRequest request);

    /**
     * Starts a rollback draft on the owner's current version whose files are those of an earlier
     * released version.
     *
     * @param user           the proposer
     * @param organizationId the organization of the request
     * @param versionId      the APPROVED or SUPERSEDED version to restore
     * @return the rollback draft, with source ROLLBACK
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_018 when the
     *         version was never released, KAY_CFG_016 when it is the current version
     */
    VersionResponse restore(User user, UUID organizationId, UUID versionId);

    /**
     * Puts the proposer's draft in review. The draft must rest on the owner's current version and
     * change it, and the owner may have no other version in review. The compositions of the
     * repositories are not checked here; the review domain checks them after it calls this method.
     *
     * @param user           the proposer
     * @param organizationId the organization of the request
     * @param draftId        the draft
     * @param reason         the reason for the change
     * @return the version, now IN_REVIEW
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_002 when a
     *         rebase is needed, KAY_CFG_014 when it is not a draft, KAY_CFG_015 when another version
     *         is in review, KAY_CFG_016 when it changes nothing
     */
    Version submit(User user, UUID organizationId, UUID draftId, String reason);

    /**
     * Creates and submits a CLI proposal on the owner's current version. A proposed path whose
     * current state is no longer the one the release delivered is a conflict, unless the proposal
     * already matches the current state.
     *
     * @param proposerId     the user behind the machine token
     * @param organizationId the organization of the token
     * @param packId         the pack proposed to
     * @param delivered      the state of each path in the release the CLI saw, each a
     *                       {@link DeliveredFile}
     * @param files          the changed files and removals
     * @param reason         the reason for the change
     * @return the proposal, IN_REVIEW with source CLI
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_002 with the
     *         stale paths, KAY_CFG_015 when another version is in review, KAY_CFG_016 when it changes
     *         nothing
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_027,
     *         KAY_CFG_028 or KAY_CFG_001 for a refused file
     */
    VersionResponse propose(UUID proposerId, UUID organizationId, UUID packId,
                            Map<String, DeliveredFile> delivered, List<ProposalFileRequest> files, String reason);

    /**
     * Imports a pack's files as they are in a repository today: an APPROVED version
     * with source SEED, the current version as base, which it supersedes, and the owner's pointer
     * moved to it. A file keeps the override marker its path has in the current version, because a
     * repository on disk cannot carry one and dropping it would undo the override. Files that
     * break only a size limit are stored, so the caller reports those limits.
     *
     * @param proposerId     the OWNER who runs the seed
     * @param organizationId the organization of the owner
     * @param packId         the pack
     * @param files          every {@link com.korur.oba.apps.kaya.render.dto.embedded.SourceFile} of
     *                       the owner, each path once
     * @param reason         the reason the version records
     * @return the new version, or empty when the files equal the current version's in content,
     *         mode, link target and override marker
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_027,
     *         KAY_CFG_028 or KAY_CFG_001 for a refused file
     */
    Optional<Version> seed(UUID proposerId, UUID organizationId, UUID packId, List<SourceFile> files,
                           String reason);

    /**
     * Moves the base of a draft's override to the current version of the nearest ancestor that
     * delivers the item, the decision "keep" or, after an edit, "update" on a parent's change. The
     * draft then differs from its base and goes through review like a content change.
     *
     * @param user           the caller, who must be the draft's proposer
     * @param organizationId the organization of the draft
     * @param draftId        the draft
     * @param request        the item and the ancestor version it is now based on
     * @return the override as recorded
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such draft or the draft holds no item at the path
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException GEN_SEC_002 when the
     *         caller did not propose the draft
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_014 when the
     *         version is not a draft; KAY_CFG_032 when the version is not the current version of the
     *         nearest ancestor delivering the item, or no ancestor delivers it
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_031 when
     *         an ancestor locks the item
     */
    ItemOverrideResponse setOverrideBase(User user, UUID organizationId, UUID draftId, ItemOverrideRequest request);

    /**
     * Removes the override record of an item no ancestor delivers any more, the decision "keep as
     * own" on a removed parent item: the item stays and is the pack's own.
     *
     * @param user           the caller, who must be the draft's proposer
     * @param organizationId the organization of the draft
     * @param draftId        the draft
     * @param path           the item, or a path inside a skill
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such draft or the draft records no override of the item
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException GEN_SEC_002 when the
     *         caller did not propose the draft
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_014 when the
     *         version is not a draft
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException GEN_VAL_001 when
     *         an ancestor still delivers the item
     */
    void clearOverride(User user, UUID organizationId, UUID draftId, String path);

    /**
     * Approves a version in review: it becomes APPROVED, the version it replaces SUPERSEDED, and the
     * owner's current-version pointer names it. Its base must still be the current version, since a
     * seed may have moved the owner on while it was in review.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the approved version
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_017 when it
     *         is not in review, KAY_CFG_002 when its base is no longer current
     */
    Version approve(UUID organizationId, UUID versionId);

    /**
     * Hands a version in review back to its proposer: a draft again, with its reason kept and the
     * decision time set.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the version, DRAFT again
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_017 when it
     *         is not in review
     */
    Version reject(UUID organizationId, UUID versionId);
}
