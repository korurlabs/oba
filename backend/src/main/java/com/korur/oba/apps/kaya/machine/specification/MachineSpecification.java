package com.korur.oba.apps.kaya.machine.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.apps.kaya.machine.constant.MachineConstant;
import com.korur.oba.apps.kaya.machine.dto.request.MachineQueryRequest;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.enums.MachineStatus;

/**
 * Filters of the machine list. Every query is limited to one organization.
 */
public final class MachineSpecification {

    private MachineSpecification() {
    }

    private static Specification<Machine> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Machine> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(MachineConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Machine> byUser(UUID userId) {
        if (userId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(MachineConstant.FIELD_USER).get(CommonConstant.ID), userId);
    }

    private static Specification<Machine> byAccount(UUID accountId) {
        if (accountId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(MachineConstant.FIELD_ACCOUNT).get(CommonConstant.ID), accountId);
    }

    private static Specification<Machine> byStatus(MachineStatus status) {
        if (status == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(MachineConstant.FIELD_STATUS), status);
    }

    public static Specification<Machine> filterBy(UUID organizationId, MachineQueryRequest request) {
        return byOrganization(organizationId)
                .and(byUser(request.getUserId()))
                .and(byAccount(request.getAccountId()))
                .and(byStatus(request.getStatus()));
    }
}
