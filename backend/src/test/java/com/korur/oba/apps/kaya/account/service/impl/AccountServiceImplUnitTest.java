package com.korur.oba.apps.kaya.account.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.account.entity.Account;
import com.korur.oba.apps.kaya.account.entity.AccountUser;
import com.korur.oba.apps.kaya.account.enums.AccountProvider;
import com.korur.oba.apps.kaya.account.enums.AccountUserSource;
import com.korur.oba.apps.kaya.account.repository.AccountRepository;
import com.korur.oba.apps.kaya.account.repository.AccountUserRepository;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.apps.kaya.identity.entity.UserAuditEntry;
import com.korur.oba.apps.kaya.identity.enums.UserAuditAction;
import com.korur.oba.apps.kaya.identity.enums.UserAuditActorKind;
import com.korur.oba.apps.kaya.identity.service.UserAuditRecorder;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-20T09:00:00Z");
    private static final String ACCOUNT_EMAIL = "ada@claude.example";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountUserRepository accountUserRepository;
    @Mock
    private KayaUserQueryService kayaUserQueryService;
    @Mock
    private UserAuditRecorder userAuditRecorder;
    @Mock
    private LoggingService loggingService;

    private AccountServiceImpl service;
    private UUID organizationId;
    private UUID machineId;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AccountServiceImpl(accountRepository, accountUserRepository, kayaUserQueryService,
                userAuditRecorder, Clock.fixed(NOW, ZoneOffset.UTC), loggingService);
        organizationId = UUID.randomUUID();
        machineId = UUID.randomUUID();
        user = User.builder().id(UUID.randomUUID())
                .profile(UserProfile.builder().firstName("Ada").lastName("Lovelace").build()).build();
    }

    private Account account() {
        return Account.builder().id(UUID.randomUUID()).organizationId(organizationId).provider(AccountProvider.CLAUDE)
                .accountEmail(ACCOUNT_EMAIL).accountOrganizationName("Acme").billingType("stripe_subscription")
                .organizationType("claude_team").seatTier("premium").lastSeenAt(NOW).build();
    }

    private Account learn() {
        return service.learnAccount(organizationId, user.getId(), machineId, AccountProvider.CLAUDE, "Ada@Claude.Example",
                null, "Acme", "stripe_subscription", "claude_team", "premium");
    }

    @Nested
    class LearnAccount {

        @Test
        void alreadyLinked_refreshesWithoutLockOrAudit() {
            Account existing = account();
            AccountUser link = AccountUser.builder().account(existing).user(user).lastSeenAt(NOW).build();
            when(accountRepository.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
            when(accountUserRepository.findByOrganizationIdAndAccount_IdAndUser_Id(organizationId, existing.getId(), user.getId()))
                    .thenReturn(Optional.of(link));

            Account result = learn();

            assertSame(existing, result);
            verify(kayaUserQueryService, never()).getMemberEntityForUpdate(any(UUID.class), any(UUID.class));
            verify(userAuditRecorder, never()).record(any(UserAuditEntry.class));
            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        void newAccount_locksTheUserCreatesTheAccountAndAuditsAccountAdded() {
            when(accountRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
            when(kayaUserQueryService.getMemberEntityForUpdate(organizationId, user.getId())).thenReturn(user);
            when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(call -> {
                Account saved = call.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });
            when(accountUserRepository.saveAndFlush(any(AccountUser.class))).thenAnswer(call -> call.getArgument(0));

            Account result = learn();

            assertEquals(ACCOUNT_EMAIL, result.getAccountEmail());
            ArgumentCaptor<AccountUser> link = ArgumentCaptor.forClass(AccountUser.class);
            verify(accountUserRepository).saveAndFlush(link.capture());
            assertSame(user, link.getValue().getUser());
            assertEquals(AccountUserSource.RECORDER, link.getValue().getSource());
            ArgumentCaptor<UserAuditEntry> entry = ArgumentCaptor.forClass(UserAuditEntry.class);
            verify(userAuditRecorder).record(entry.capture());
            assertEquals(UserAuditAction.ACCOUNT_ADDED, entry.getValue().getAction());
            assertEquals(UserAuditActorKind.RECORDER, entry.getValue().getActorKind());
            assertSame(user, entry.getValue().getUser());
            assertEquals(result.getId().toString(), entry.getValue().getDetails().get("accountId"));
            assertEquals(machineId.toString(), entry.getValue().getDetails().get("machineId"));
        }

        @Test
        void existingAccountOfAnotherUser_isLinkedWithoutCreatingAndTakesTheReport() {
            Account existing = account();
            existing.setSeatTier("standard");
            when(accountRepository.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
            when(accountUserRepository.findByOrganizationIdAndAccount_IdAndUser_Id(organizationId, existing.getId(), user.getId()))
                    .thenReturn(Optional.empty());
            when(kayaUserQueryService.getMemberEntityForUpdate(organizationId, user.getId())).thenReturn(user);
            when(accountRepository.save(existing)).thenReturn(existing);
            when(accountUserRepository.saveAndFlush(any(AccountUser.class))).thenAnswer(call -> call.getArgument(0));

            Account result = learn();

            assertSame(existing, result);
            assertEquals("premium", existing.getSeatTier());
            verify(accountRepository, never()).saveAndFlush(any(Account.class));
        }

        @Test
        void linkedByAConcurrentBatchWhileWaitingForTheLock_refreshesWithoutLinking() {
            Account existing = account();
            AccountUser link = AccountUser.builder().account(existing).user(user).lastSeenAt(NOW).build();
            when(accountRepository.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
            when(accountUserRepository.findByOrganizationIdAndAccount_IdAndUser_Id(organizationId, existing.getId(), user.getId()))
                    .thenReturn(Optional.empty(), Optional.of(link));
            when(kayaUserQueryService.getMemberEntityForUpdate(organizationId, user.getId())).thenReturn(user);

            Account result = learn();

            assertSame(existing, result);
            verify(accountUserRepository, never()).saveAndFlush(any(AccountUser.class));
            verify(userAuditRecorder, never()).record(any(UserAuditEntry.class));
        }

        @Test
        void userNotAMember_throwsNotFoundAndWritesNothing() {
            when(accountRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
            when(kayaUserQueryService.getMemberEntityForUpdate(organizationId, user.getId()))
                    .thenThrow(new NotFoundException(ExceptionDomainKayaError.NOT_RESOURCE_FOUND,
                            Map.of("userId", user.getId().toString())));

            assertThrows(NotFoundException.class, AccountServiceImplUnitTest.this::learn);

            verify(accountRepository, never()).saveAndFlush(any(Account.class));
            verify(accountUserRepository, never()).saveAndFlush(any(AccountUser.class));
        }

        @Test
        void concurrentBatchCreatedTheAccountFirst_throwsLearnedConcurrently() {
            when(accountRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
            when(kayaUserQueryService.getMemberEntityForUpdate(organizationId, user.getId())).thenReturn(user);
            when(accountRepository.saveAndFlush(any(Account.class))).thenThrow(new DataIntegrityViolationException("uk"));

            ConflictException exception = assertThrows(ConflictException.class, AccountServiceImplUnitTest.this::learn);

            assertEquals("KAY_VAL_016", exception.getErrorCode());
            verify(accountUserRepository, never()).saveAndFlush(any(AccountUser.class));
            verify(userAuditRecorder, never()).record(any(UserAuditEntry.class));
        }

        @Test
        void concurrentBatchLinkedTheUserFirst_throwsLearnedConcurrently() {
            Account existing = account();
            when(accountRepository.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
            when(accountUserRepository.findByOrganizationIdAndAccount_IdAndUser_Id(organizationId, existing.getId(), user.getId()))
                    .thenReturn(Optional.empty());
            when(kayaUserQueryService.getMemberEntityForUpdate(organizationId, user.getId())).thenReturn(user);
            when(accountRepository.save(existing)).thenReturn(existing);
            when(accountUserRepository.saveAndFlush(any(AccountUser.class)))
                    .thenThrow(new DataIntegrityViolationException("uk"));

            ConflictException exception = assertThrows(ConflictException.class, AccountServiceImplUnitTest.this::learn);

            assertEquals("KAY_VAL_016", exception.getErrorCode());
            verify(userAuditRecorder, never()).record(any(UserAuditEntry.class));
        }
    }

    @Nested
    class UnlinkUser {

        @Test
        void userNotLinked_throwsNotFound() {
            UUID accountId = UUID.randomUUID();
            when(accountUserRepository.findByOrganizationIdAndAccount_IdAndUser_Id(organizationId, accountId, user.getId()))
                    .thenReturn(Optional.empty());

            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> service.unlinkUser(organizationId, accountId, user.getId()));

            assertEquals("KAY_NF_001", exception.getErrorCode());
            verify(accountRepository, never()).delete(any(Account.class));
        }

        @Test
        void otherLinksRemain_keepsTheAccount() {
            Account existing = account();
            AccountUser link = AccountUser.builder().account(existing).user(user).build();
            when(accountUserRepository.findByOrganizationIdAndAccount_IdAndUser_Id(organizationId, existing.getId(), user.getId()))
                    .thenReturn(Optional.of(link));
            when(accountUserRepository.existsByAccount_Id(existing.getId())).thenReturn(true);

            Account removed = service.unlinkUser(organizationId, existing.getId(), user.getId());

            assertSame(existing, removed);
            verify(accountUserRepository).delete(link);
            verify(accountRepository, never()).delete(any(Account.class));
        }

        @Test
        void lastLink_deletesTheAccount() {
            Account existing = account();
            AccountUser link = AccountUser.builder().account(existing).user(user).build();
            when(accountUserRepository.findByOrganizationIdAndAccount_IdAndUser_Id(organizationId, existing.getId(), user.getId()))
                    .thenReturn(Optional.of(link));
            when(accountUserRepository.existsByAccount_Id(existing.getId())).thenReturn(false);

            service.unlinkUser(organizationId, existing.getId(), user.getId());

            verify(accountRepository).delete(existing);
        }
    }
}
