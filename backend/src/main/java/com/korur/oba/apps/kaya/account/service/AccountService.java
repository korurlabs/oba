package com.korur.oba.apps.kaya.account.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.account.entity.Account;
import com.korur.oba.apps.kaya.account.enums.AccountProvider;

/**
 * Write operations on accounts and their user links. Both run inside the caller's transaction
 * when there is one, so a refused ingest batch or admin request leaves no partial change.
 */
public interface AccountService {

    /**
     * Records that the user of a machine token uses the reported account. An account the user is
     * already linked to is refreshed: the subscription fields and the last-seen times of the
     * account and of the link, written only when a field changed or a last-seen time is a minute
     * old, so steady batches do not rewrite them and take no lock. Otherwise the user row is
     * locked, the link is checked again, and the user is linked with source RECORDER, creating
     * the account when the organization has none; the new link is audited as ACCOUNT_ADDED by
     * the RECORDER.
     *
     * @param organizationId          the organization of the machine token
     * @param userId                  the user of the machine token
     * @param machineId                 the machine that sent the batch, for the audit entry
     * @param provider                the AI provider
     * @param accountEmail            the account email as reported; it is normalized here
     * @param accountOrganizationUuid the provider organization UUID, or null for a personal account
     * @param accountOrganizationName the provider organization name, or null
     * @param billingType             the billing type, or null
     * @param organizationType        the organization type, or null
     * @param seatTier                the seat tier, or null
     * @return the account the user is linked to
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         user is not a member of the organization
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_VAL_016 when a
     *         concurrent batch created the account or the link first
     */
    Account learnAccount(UUID organizationId, UUID userId, UUID machineId, AccountProvider provider,
            String accountEmail, String accountOrganizationUuid, String accountOrganizationName, String billingType,
            String organizationType, String seatTier);

    /**
     * Removes an account from one user. The account is deleted with its last link; machines and
     * usage snapshots that pointed at it keep no account. The caller checks access and audits.
     *
     * @param organizationId the organization of the request
     * @param accountId      the account
     * @param userId         the user
     * @return the account as it was, for the audit entry
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         user is not linked to the account in this organization
     */
    Account unlinkUser(UUID organizationId, UUID accountId, UUID userId);
}
