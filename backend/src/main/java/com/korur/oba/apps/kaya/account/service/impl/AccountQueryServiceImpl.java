package com.korur.oba.apps.kaya.account.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.account.constant.AccountConstant;
import com.korur.oba.apps.kaya.account.constant.service.AccountServiceLogConstant;
import com.korur.oba.apps.kaya.account.dto.request.AccountQueryRequest;
import com.korur.oba.apps.kaya.account.dto.response.AccountDetailResponse;
import com.korur.oba.apps.kaya.account.dto.response.AccountListResponse;
import com.korur.oba.apps.kaya.account.entity.Account;
import com.korur.oba.apps.kaya.account.entity.AccountUser;
import com.korur.oba.apps.kaya.account.mapper.AccountMapper;
import com.korur.oba.apps.kaya.account.repository.AccountRepository;
import com.korur.oba.apps.kaya.account.repository.AccountUserRepository;
import com.korur.oba.apps.kaya.account.service.AccountQueryService;
import com.korur.oba.apps.kaya.account.specification.AccountSpecification;
import com.korur.oba.apps.kaya.identity.helper.UserDisplayNameHelper;
import com.korur.oba.apps.kaya.machine.dto.response.MachineAccountSummaryResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageResponse;
import com.korur.oba.apps.kaya.machine.service.MachineQueryService;
import com.korur.oba.apps.kaya.machine.service.MachineUsageQueryService;

/**
 * Reads accounts. User names come through the links' user relation rather than the user query
 * service, because the user views of the identity domain read accounts through this service.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryServiceImpl implements AccountQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AccountQueryServiceImpl.class);

    private final AccountRepository accountRepository;
    private final AccountUserRepository accountUserRepository;
    private final MachineQueryService machineQueryService;
    private final MachineUsageQueryService machineUsageQueryService;
    private final AccountMapper accountMapper;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    public List<AccountListResponse> getAccounts(UUID organizationId, AccountQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.LISTING_ACCOUNTS, organizationId, request);

        Sort sort = Sort.by(AccountConstant.FIELD_ACCOUNT_EMAIL)
                .and(Sort.by(AccountConstant.FIELD_ACCOUNT_ORGANIZATION_NAME))
                .and(Sort.by(CommonConstant.ID));
        List<Account> accounts = accountRepository.findAll(AccountSpecification.filterBy(organizationId, request), sort);

        // The users, machine counts and usage of the whole list come in three queries instead of three per account.
        List<UUID> accountIds = accounts.stream().map(Account::getId).toList();
        Map<UUID, List<AccountUser>> usersByAccount = linksByAccount(organizationId, accountIds);
        Map<UUID, MachineAccountSummaryResponse> machinesByAccount = machineQueryService.getAccountSummaries(organizationId, accountIds);
        Map<UUID, MachineUsageResponse> usageByAccount =
                machineUsageQueryService.usageForAccounts(organizationId, accountIds, Instant.now(clock));

        return accounts.stream()
                .map(account -> accountMapper.toListResponse(account,
                        usersByAccount.getOrDefault(account.getId(), List.of()),
                        machinesByAccount.get(account.getId()),
                        usageByAccount.get(account.getId())))
                .toList();
    }

    @Override
    public AccountDetailResponse getAccount(UUID organizationId, UUID accountId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.READING_ACCOUNT, accountId, organizationId);
        // Looked up within the organization, so an account of another organization answers the
        // same 404 as an unknown id.
        Account account = accountRepository.findByIdAndOrganizationId(accountId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.ACCOUNT_NOT_FOUND, accountId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_RESOURCE_FOUND,
                    Map.of(AccountConstant.FIELD_ACCOUNT_ID, accountId.toString()));
        });
        List<UUID> accountIds = List.of(accountId);
        return accountMapper.toDetailResponse(account,
                linksByAccount(organizationId, accountIds).getOrDefault(accountId, List.of()),
                machineQueryService.getAccountSummaries(organizationId, accountIds).get(accountId),
                machineUsageQueryService.usageForAccounts(organizationId, accountIds, Instant.now(clock)).get(accountId));
    }

    @Override
    public Map<UUID, List<AccountUser>> getUserAccounts(UUID organizationId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        // Grouped into an insertion-ordered map with list values, so each user keeps the
        // repository's account order.
        return accountUserRepository
                .findByOrganizationIdAndUser_IdInOrderByAccount_AccountEmailAscAccount_AccountOrganizationNameAsc(
                        organizationId, userIds)
                .stream()
                .collect(Collectors.groupingBy(link -> link.getUser().getId(), LinkedHashMap::new,
                        Collectors.toList()));
    }

    /**
     * Loads the user links of the accounts with the user, grouped per account in display-name
     * order. The platform user has no stored display name, so the order is built here from the
     * same name every response shows.
     */
    private Map<UUID, List<AccountUser>> linksByAccount(UUID organizationId, List<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accountUserRepository
                .findByOrganizationIdAndAccount_IdIn(organizationId, accountIds)
                .stream()
                .sorted(Comparator.comparing((AccountUser link) -> UserDisplayNameHelper.displayNameOf(link.getUser()))
                        .thenComparing(link -> link.getUser().getId()))
                .collect(Collectors.groupingBy(link -> link.getAccount().getId(), LinkedHashMap::new,
                        Collectors.toList()));
    }
}
