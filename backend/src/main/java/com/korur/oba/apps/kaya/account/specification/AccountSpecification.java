package com.korur.oba.apps.kaya.account.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.apps.kaya.account.constant.AccountConstant;
import com.korur.oba.apps.kaya.account.dto.request.AccountQueryRequest;
import com.korur.oba.apps.kaya.account.entity.Account;
import com.korur.oba.apps.kaya.account.entity.AccountUser;

/**
 * Filters of the account list. Every list is limited to one organization.
 */
public final class AccountSpecification {

    private AccountSpecification() {
    }

    private static Specification<Account> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Account> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(AccountConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    /**
     * Matches the accounts the user is linked to. A subquery rather than a join, so an account
     * appears once however many users use it.
     */
    private static Specification<Account> byUser(UUID userId) {
        if (userId == null) {
            return noOp();
        }
        return (root, query, cb) -> {
            Subquery<UUID> linked = query.subquery(UUID.class);
            Root<AccountUser> link = linked.from(AccountUser.class);
            linked.select(link.get(CommonConstant.ID)).where(
                    cb.equal(link.get(AccountConstant.FIELD_ACCOUNT), root),
                    cb.equal(link.get(AccountConstant.FIELD_USER).get(CommonConstant.ID), userId));
            return cb.exists(linked);
        };
    }

    public static Specification<Account> filterBy(UUID organizationId, AccountQueryRequest request) {
        return byOrganization(organizationId)
                .and(byUser(request.getUserId()));
    }
}
