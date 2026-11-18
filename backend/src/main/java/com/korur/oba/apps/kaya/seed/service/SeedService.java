package com.korur.oba.apps.kaya.seed.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.seed.dto.request.SeedRequest;
import com.korur.oba.apps.kaya.seed.dto.response.SeedRunResponse;

/**
 * Imports today's repository files into the organization's
 * {@link com.korur.oba.apps.kaya.pack.entity.Pack} tree: it finds or creates each pack under its
 * parent through {@link com.korur.oba.apps.kaya.pack.service.PackService}, screens the files with
 * {@link com.korur.oba.apps.kaya.validation.service.ValidationQueryService} and
 * {@link com.korur.oba.apps.kaya.render.service.RenderQueryService}, writes each named repository's
 * pack list with its mount folders through
 * {@link com.korur.oba.apps.kaya.release.service.RepositoryPackService#writePacks}, writes one
 * approved SEED version per pack whose files changed through
 * {@link com.korur.oba.apps.kaya.version.service.VersionService} and signs one release per
 * repository the run reaches through {@link com.korur.oba.apps.kaya.release.service.ReleaseService}.
 * It writes {@link com.korur.oba.apps.kaya.seed.entity.SeedRun}. A second run with the same input
 * creates no version and no release. Called by
 * {@link com.korur.oba.apps.kaya.seed.controller.SeedController}.
 */
public interface SeedService {

    /**
     * Runs a seed and stores it with its report.
     *
     * A file that breaks a file rule, or that render refuses as NOT_DELIVERABLE or INVALID_FILE, is
     * left out of its set and listed under report.refused, and each link that leads to or through it
     * is left out and listed under report.notImported; the run still imports the rest and can pass.
     * A file over a size limit is imported and listed under report.warnings. A
     * repository composition that breaks a rule, or a changed pack no repository composes that
     * stores an item its ancestors lock, discards every write of the run: the run is stored FAILED
     * with report.applied false and the violations. Drift the CLI reports also makes the run
     * FAILED, without changing what is imported.
     *
     * @param owner          the OWNER running the seed
     * @param organizationId the organization of the request
     * @param request        the sets, the repositories with their packs, and what the CLI left out
     *                       or found drifted
     * @return the stored run
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException GEN_VAL_001 for a
     *         malformed request (a set or path listed twice, a parent that is the pack
     *         itself, unknown or part of a cycle, a repository pack that is unknown, a repository
     *         listed twice), KAY_CFG_009 when a named blob is not uploaded, KAY_CFG_026 for an
     *         invalid mount folder, KAY_CFG_001 for a pack a repository lists twice, KAY_CFG_034
     *         when a rule of a mounted pack is not scoped to each folder the pack is mounted at;
     *         nothing is stored then
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_033 when a
     *         stored pack has another parent than the request names; nothing is stored
     * @throws com.korur.oba.dojo.exception.internal.business.BadRequestException GEN_API_001 for a
     *         remote Kaya does not accept
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException KAY_CFG_011
     *         (413) when the request names more files or content than one run takes
     */
    SeedRunResponse seed(User owner, UUID organizationId, SeedRequest request);
}
