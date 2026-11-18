package com.korur.oba.apps.kaya.account.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.account.dto.request.AccountQueryRequest;
import com.korur.oba.apps.kaya.account.dto.response.AccountDetailResponse;
import com.korur.oba.apps.kaya.account.dto.response.AccountListResponse;
import com.korur.oba.apps.kaya.account.entity.AccountUser;

/**
 * Reads accounts and their user links. Other domains use it for the accounts of a user,
 * never the account tables.
 */
public interface AccountQueryService {

    /**
     * Lists the accounts of the organization with their users, machine counts and current usage,
     * ordered by account email, provider organization name and id. Not paged.
     *
     * @param organizationId the organization of the request
     * @param request        the filters
     * @return the accounts; an organization without accounts gives an empty list
     */
    List<AccountListResponse> getAccounts(UUID organizationId, AccountQueryRequest request);

    /**
     * Reads one account of the organization with its users, machine counts and current usage.
     *
     * @param organizationId the organization of the request
     * @param accountId      the account
     * @return the account
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such account, including an account of another organization
     */
    AccountDetailResponse getAccount(UUID organizationId, UUID accountId);

    /**
     * Lists the account links of each given user, with the account loaded, ordered by account
     * email and provider organization name.
     *
     * @param organizationId the organization of the users
     * @param userIds        the users; may be empty
     * @return the links per user id; a user without accounts has no entry
     */
    Map<UUID, List<AccountUser>> getUserAccounts(UUID organizationId, Collection<UUID> userIds);
}
