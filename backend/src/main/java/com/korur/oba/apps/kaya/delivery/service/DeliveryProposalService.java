package com.korur.oba.apps.kaya.delivery.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.delivery.dto.request.DeliveryProposalRequest;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryProposalResponse;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

/**
 * A change proposed from a checkout with a machine token, as kaya propose sends it. The proposer is
 * the user of the token, so a proposal is reviewed under the same rules as a
 * change made in the web app: never by its proposer, and by an OWNER when it is executable.
 *
 * It reads the release through {@link com.korur.oba.apps.kaya.release.service.ReleaseQueryService},
 * the owning pack through {@link com.korur.oba.apps.kaya.pack.service.PackQueryService}, and writes the CLI draft
 * through {@link com.korur.oba.apps.kaya.version.service.VersionService#propose}. Called by
 * {@link com.korur.oba.apps.kaya.delivery.controller.DeliveryController}.
 */
public interface DeliveryProposalService {

    /**
     * Resolves the checkout's repository, the release the files were edited on and the one pack
     * that owns the proposed paths, then creates its CLI draft and submits it for review. A path
     * belongs to the pack whose version the release delivered it from. A path the release did not
     * deliver belongs to the mounted pack whose namespace or folder AGENTS.md holds it, else to the
     * deepest pack the repository composes at its root. A mounted pack's folder AGENTS.md is stored
     * as AGENTS.md.
     *
     * The machine token chain has already refused a token whose user no longer acts in the
     * organization with the Kaya app, so the user of the token is the proposer.
     *
     * @param principal the machine token's principal
     * @param request   the remote, the release number, the reason and the files
     * @return the proposal in review with its review page
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 for an
     *         unknown repository or release number
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_019 when
     *         the paths come from several packs (errorValues owners), or a new path has no single
     *         deepest root pack (errorValues candidates), GEN_VAL_001 for a path named twice, and
     *         the refusals of {@link com.korur.oba.apps.kaya.version.service.VersionService#propose}
     */
    DeliveryProposalResponse propose(MachinePrincipal principal, DeliveryProposalRequest request);
}
