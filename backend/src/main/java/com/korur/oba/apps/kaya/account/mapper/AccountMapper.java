package com.korur.oba.apps.kaya.account.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.identity.helper.UserDisplayNameHelper;
import com.korur.oba.apps.kaya.account.dto.response.AccountDetailResponse;
import com.korur.oba.apps.kaya.account.dto.response.AccountListResponse;
import com.korur.oba.apps.kaya.account.dto.response.embedded.AccountUserEmbeddedResponse;
import com.korur.oba.apps.kaya.account.entity.Account;
import com.korur.oba.apps.kaya.account.entity.AccountUser;
import com.korur.oba.apps.kaya.machine.dto.response.MachineAccountSummaryResponse;
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageResponse;

/**
 * Maps accounts to responses. The user links, the machine summary and the usage are passed in by
 * the caller, which loads them for a whole list at once. An account without machines has no summary
 * and counts zero machines.
 */
@Mapper(componentModel = "spring", imports = UserDisplayNameHelper.class)
public interface AccountMapper {

    @Mapping(target = "id", source = "account.id")
    @Mapping(target = "provider", source = "account.provider")
    @Mapping(target = "accountEmail", source = "account.accountEmail")
    @Mapping(target = "accountOrganizationUuid", source = "account.accountOrganizationUuid")
    @Mapping(target = "accountOrganizationName", source = "account.accountOrganizationName")
    @Mapping(target = "billingType", source = "account.billingType")
    @Mapping(target = "organizationType", source = "account.organizationType")
    @Mapping(target = "seatTier", source = "account.seatTier")
    @Mapping(target = "lastSeenAt", source = "account.lastSeenAt")
    @Mapping(target = "users", source = "users")
    @Mapping(target = "machines", source = "machineSummary.machines", defaultValue = "0")
    @Mapping(target = "activeMachines", source = "machineSummary.activeMachines", defaultValue = "0")
    @Mapping(target = "usage", source = "usage")
    AccountListResponse toListResponse(Account account, List<AccountUser> users,
            MachineAccountSummaryResponse machineSummary, MachineUsageResponse usage);

    @Mapping(target = "id", source = "account.id")
    @Mapping(target = "provider", source = "account.provider")
    @Mapping(target = "accountEmail", source = "account.accountEmail")
    @Mapping(target = "accountOrganizationUuid", source = "account.accountOrganizationUuid")
    @Mapping(target = "accountOrganizationName", source = "account.accountOrganizationName")
    @Mapping(target = "billingType", source = "account.billingType")
    @Mapping(target = "organizationType", source = "account.organizationType")
    @Mapping(target = "seatTier", source = "account.seatTier")
    @Mapping(target = "lastSeenAt", source = "account.lastSeenAt")
    @Mapping(target = "users", source = "users")
    @Mapping(target = "machines", source = "machineSummary.machines", defaultValue = "0")
    @Mapping(target = "activeMachines", source = "machineSummary.activeMachines", defaultValue = "0")
    @Mapping(target = "usage", source = "usage")
    @Mapping(target = "createdAt", source = "account.createdAt")
    @Mapping(target = "updatedAt", source = "account.updatedAt")
    AccountDetailResponse toDetailResponse(Account account, List<AccountUser> users,
            MachineAccountSummaryResponse machineSummary, MachineUsageResponse usage);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "displayName", expression = "java(UserDisplayNameHelper.displayNameOf(link.getUser()))")
    @Mapping(target = "source", source = "source")
    @Mapping(target = "lastSeenAt", source = "lastSeenAt")
    AccountUserEmbeddedResponse toUserEmbeddedResponse(AccountUser link);
}
