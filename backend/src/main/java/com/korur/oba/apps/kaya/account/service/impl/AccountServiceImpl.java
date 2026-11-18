package com.korur.oba.apps.kaya.account.service.impl;

// 1. Framework-level
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.account.constant.AccountConstant;
import com.korur.oba.apps.kaya.account.constant.service.AccountServiceLogConstant;
import com.korur.oba.apps.kaya.account.entity.Account;
import com.korur.oba.apps.kaya.account.entity.AccountUser;
import com.korur.oba.apps.kaya.account.enums.AccountProvider;
import com.korur.oba.apps.kaya.account.enums.AccountUserSource;
import com.korur.oba.apps.kaya.account.repository.AccountRepository;
import com.korur.oba.apps.kaya.account.repository.AccountUserRepository;
import com.korur.oba.apps.kaya.account.service.AccountService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.identity.entity.UserAuditEntry;
import com.korur.oba.apps.kaya.identity.enums.UserAuditAction;
import com.korur.oba.apps.kaya.identity.enums.UserAuditActorKind;
import com.korur.oba.apps.kaya.identity.helper.EmailDomainHelper;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.UserAuditRecorder;
import com.korur.oba.apps.kaya.machine.constant.MachineConstant;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AccountServiceImpl.class);

    private final AccountRepository accountRepository;
    private final AccountUserRepository accountUserRepository;
    private final KayaUserQueryService kayaUserQueryService;
    private final UserAuditRecorder userAuditRecorder;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public Account learnAccount(UUID organizationId, UUID userId, UUID machineId, AccountProvider provider,
            String accountEmail, String accountOrganizationUuid, String accountOrganizationName, String billingType,
            String organizationType, String seatTier) {
        AccountKey key = new AccountKey(provider, EmailDomainHelper.normalizeEmail(accountEmail), accountOrganizationUuid);
        AccountReport report = new AccountReport(accountOrganizationName, billingType, organizationType, seatTier);
        Optional<Account> linked = refreshIfLinked(organizationId, userId, key, report);
        if (linked.isPresent()) {
            return linked.get();
        }

        // Locked only to link, so steady batches of a linked account take no lock; the second
        // check sees a link a concurrent batch of the same user made meanwhile.
        User user = kayaUserQueryService.getMemberEntityForUpdate(organizationId, userId);
        linked = refreshIfLinked(organizationId, userId, key, report);
        if (linked.isPresent()) {
            return linked.get();
        }

        AccountUser link = link(organizationId, user, key, report);
        Account account = link.getAccount();
        Map<String, Object> details = new HashMap<>();
        details.put(AccountConstant.FIELD_ACCOUNT_ID, account.getId().toString());
        details.put(AccountConstant.FIELD_PROVIDER, provider.name());
        details.put(AccountConstant.FIELD_ACCOUNT_EMAIL, key.accountEmail());
        details.put(AccountConstant.FIELD_ACCOUNT_ORGANIZATION_UUID, accountOrganizationUuid);
        details.put(AccountConstant.FIELD_ACCOUNT_ORGANIZATION_NAME, accountOrganizationName);
        details.put(AccountConstant.FIELD_SOURCE, link.getSource().name());
        details.put(MachineConstant.FIELD_MACHINE_ID, machineId.toString());
        userAuditRecorder.record(UserAuditEntry.builder()
                .organizationId(organizationId)
                .user(user)
                .actorKind(UserAuditActorKind.RECORDER)
                .action(UserAuditAction.ACCOUNT_ADDED)
                .details(details)
                .build());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.ACCOUNT_LEARNED,
                account.getId(), userId, organizationId, machineId);
        return account;
    }

    @Override
    @Transactional
    public Account unlinkUser(UUID organizationId, UUID accountId, UUID userId) {
        // Looked up within the organization, so an account of another organization answers the
        // same 404 as an unknown id.
        AccountUser link = accountUserRepository
                .findByOrganizationIdAndAccount_IdAndUser_Id(organizationId, accountId, userId)
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.LINK_NOT_FOUND,
                            accountId, userId, organizationId);
                    return new NotFoundException(ExceptionDomainKayaError.NOT_RESOURCE_FOUND,
                            Map.of(AccountConstant.FIELD_ACCOUNT_ID, accountId.toString()));
                });
        Account account = link.getAccount();

        accountUserRepository.delete(link);
        accountUserRepository.flush();
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.ACCOUNT_UNLINKED,
                accountId, userId, organizationId);

        // An account exists only while a user uses it; machines and snapshots lose their
        // reference through the ON DELETE SET NULL foreign keys.
        if (!accountUserRepository.existsByAccount_Id(accountId)) {
            accountRepository.delete(account);
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.ACCOUNT_DELETED,
                    accountId, organizationId);
        }
        return account;
    }

    /**
     * Refreshes the account of the key when the user is already linked to it.
     *
     * @return the account, or empty when no such account exists or the user is not linked to it
     */
    private Optional<Account> refreshIfLinked(UUID organizationId, UUID userId, AccountKey key, AccountReport report) {
        Optional<Account> found = findAccount(organizationId, key);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Account account = found.get();
        Optional<AccountUser> link = accountUserRepository
                .findByOrganizationIdAndAccount_IdAndUser_Id(organizationId, account.getId(), userId);
        if (link.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(refresh(account, link.get(), report, userId));
    }

    /**
     * Links the locked user to the account of the key with source RECORDER, creating the account
     * when the organization has none. An existing account takes the reported subscription fields.
     */
    private AccountUser link(UUID organizationId, User user, AccountKey key, AccountReport report) {
        Instant now = Instant.now(clock);
        Optional<Account> found = findAccount(organizationId, key);
        Account account;
        if (found.isPresent()) {
            account = found.get();
            applyReport(account, report, now);
            account = accountRepository.save(account);
        } else {
            account = insertAccount(organizationId, user.getId(), key, report, now);
        }

        AccountUser link;
        try {
            // Flushed here, so a link made by a concurrent request after the caller's check hits
            // the unique constraint now and the batch gets 409 instead of 500 at commit.
            link = accountUserRepository.saveAndFlush(AccountUser.builder()
                    .organizationId(organizationId)
                    .account(account)
                    .user(user)
                    .source(AccountUserSource.RECORDER)
                    .lastSeenAt(now)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.LINK_LEARNED_CONCURRENTLY,
                    account.getId(), user.getId());
            throw new ConflictException(ExceptionDomainKayaError.VAL_ACCOUNT_LEARNED_CONCURRENTLY,
                    Map.of(AccountConstant.FIELD_ACCOUNT_ID, account.getId().toString(),
                            AccountConstant.FIELD_USER_ID, user.getId().toString()));
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.ACCOUNT_LINKED,
                account.getId(), user.getId(), organizationId);
        return link;
    }

    private Optional<Account> findAccount(UUID organizationId, AccountKey key) {
        // A personal account has no organization UUID; the unique key treats nulls as equal, so
        // the lookup must match null with IS NULL rather than with =.
        Specification<Account> specification = (root, query, cb) -> cb.and(
                cb.equal(root.get(AccountConstant.FIELD_ORGANIZATION_ID), organizationId),
                cb.equal(root.get(AccountConstant.FIELD_PROVIDER), key.provider()),
                cb.equal(root.get(AccountConstant.FIELD_ACCOUNT_EMAIL), key.accountEmail()),
                key.accountOrganizationUuid() == null
                        ? cb.isNull(root.get(AccountConstant.FIELD_ACCOUNT_ORGANIZATION_UUID))
                        : cb.equal(root.get(AccountConstant.FIELD_ACCOUNT_ORGANIZATION_UUID),
                                key.accountOrganizationUuid()));
        return accountRepository.findOne(specification);
    }

    private Account insertAccount(UUID organizationId, UUID userId, AccountKey key, AccountReport report,
            Instant now) {
        Account account;
        try {
            // Flushed here, so an account created by a concurrent batch after the lookup hits the
            // unique constraint now and the batch gets 409 instead of 500 at commit.
            account = accountRepository.saveAndFlush(Account.builder()
                    .organizationId(organizationId)
                    .provider(key.provider())
                    .accountEmail(key.accountEmail())
                    .accountOrganizationUuid(key.accountOrganizationUuid())
                    .accountOrganizationName(report.accountOrganizationName())
                    .billingType(report.billingType())
                    .organizationType(report.organizationType())
                    .seatTier(report.seatTier())
                    .lastSeenAt(now)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.ACCOUNT_LEARNED_CONCURRENTLY,
                    key.provider(), key.accountEmail(), organizationId);
            throw new ConflictException(ExceptionDomainKayaError.VAL_ACCOUNT_LEARNED_CONCURRENTLY,
                    Map.of(AccountConstant.FIELD_ACCOUNT_EMAIL, key.accountEmail()));
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.ACCOUNT_CREATED,
                key.provider(), account.getId(), organizationId, userId);
        return account;
    }

    /**
     * Writes a batch's report to a linked account and its link, at most once a minute unless a
     * subscription field changed.
     */
    private Account refresh(Account account, AccountUser link, AccountReport report, UUID userId) {
        Instant now = Instant.now(clock);
        Instant staleBefore = now.minusSeconds(AccountConstant.SEEN_REFRESH_SECONDS);
        boolean changed = !Objects.equals(report.accountOrganizationName(), account.getAccountOrganizationName())
                || !Objects.equals(report.billingType(), account.getBillingType())
                || !Objects.equals(report.organizationType(), account.getOrganizationType())
                || !Objects.equals(report.seatTier(), account.getSeatTier());
        boolean accountStale = account.getLastSeenAt().isBefore(staleBefore);
        boolean linkStale = link.getLastSeenAt().isBefore(staleBefore);
        if (!changed && !accountStale && !linkStale) {
            return account;
        }
        if (changed || accountStale) {
            applyReport(account, report, now);
            account = accountRepository.save(account);
        }
        if (linkStale) {
            link.setLastSeenAt(now);
            accountUserRepository.save(link);
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, AccountServiceLogConstant.ACCOUNT_REFRESHED,
                account.getId(), userId);
        return account;
    }

    private void applyReport(Account account, AccountReport report, Instant now) {
        account.setAccountOrganizationName(report.accountOrganizationName());
        account.setBillingType(report.billingType());
        account.setOrganizationType(report.organizationType());
        account.setSeatTier(report.seatTier());
        account.setLastSeenAt(now);
    }

    /**
     * What identifies an account within an organization. It matches the unique key of
     * kaya.accounts, where a null organization UUID (a personal account) equals another null.
     *
     * @param provider                the AI provider
     * @param accountEmail            the account email, normalized
     * @param accountOrganizationUuid the provider organization UUID, or null for a personal account
     */
    private record AccountKey(AccountProvider provider, String accountEmail, String accountOrganizationUuid) {
    }

    /**
     * The subscription fields a recorder reports with an account; any of them may be null.
     *
     * @param accountOrganizationName the provider organization name
     * @param billingType             the billing type
     * @param organizationType        the organization type
     * @param seatTier                the seat tier
     */
    private record AccountReport(String accountOrganizationName, String billingType, String organizationType,
            String seatTier) {
    }
}
