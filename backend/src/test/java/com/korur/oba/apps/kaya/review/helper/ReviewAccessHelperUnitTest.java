package com.korur.oba.apps.kaya.review.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.review.constant.ReviewConstant;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionSource;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewAccessHelperUnitTest {

    private final ReviewAccessHelper helper = new ReviewAccessHelper();
    private final UUID proposer = UUID.randomUUID();
    private final UUID reviewer = UUID.randomUUID();

    @Test
    void requiredRole_executableChange_needsOwner() {
        assertEquals(OrganizationMemberRole.OWNER, helper.requiredRole(true));
        assertEquals(OrganizationMemberRole.ADMIN, helper.requiredRole(false));
        assertEquals(OrganizationMemberRole.ADMIN, helper.requiredRole(null));
    }

    @Test
    void refusal_adminApprovesPlainChange_allowed() {
        assertEquals(Optional.empty(), helper.refusal(reviewer, OrganizationMemberRole.ADMIN, version(false, VersionSource.MANUAL), true));
    }

    @Test
    void refusal_adminApprovesExecutableChange_refusedForRole() {
        assertEquals(Optional.of(ReviewConstant.REASON_ROLE),
                helper.refusal(reviewer, OrganizationMemberRole.ADMIN, version(true, VersionSource.MANUAL), true));
    }

    @Test
    void refusal_adminRejectsExecutableChange_allowed() {
        assertEquals(Optional.empty(), helper.refusal(reviewer, OrganizationMemberRole.ADMIN, version(true, VersionSource.MANUAL), false));
    }

    @Test
    void refusal_member_refusedForRole() {
        assertEquals(Optional.of(ReviewConstant.REASON_ROLE),
                helper.refusal(reviewer, OrganizationMemberRole.MEMBER, version(false, VersionSource.MANUAL), false));
    }

    @Test
    void refusal_ownerProposedRollback_refusedAsProposer() {
        assertEquals(Optional.of(ReviewConstant.REASON_PROPOSER),
                helper.refusal(proposer, OrganizationMemberRole.OWNER, version(false, VersionSource.ROLLBACK), true));
    }

    private Version version(boolean executable, VersionSource source) {
        return Version.builder().id(UUID.randomUUID()).proposedBy(proposer).executableChange(executable).source(source).build();
    }
}
