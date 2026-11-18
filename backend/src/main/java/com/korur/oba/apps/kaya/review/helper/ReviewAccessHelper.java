package com.korur.oba.apps.kaya.review.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.review.constant.ReviewConstant;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Who may review a {@link Version}. An ADMIN or OWNER who did not propose it may reject it or
 * comment on it; approving it needs an OWNER when it changes something that runs on a developer's
 * machine (an executable change, flagged at submit), otherwise an ADMIN. The proposer never
 * reviews their own change.
 */
@Component
public class ReviewAccessHelper {

    /**
     * The least role that may approve the version, from its executable-change flag.
     *
     * @param executableChange the flag of the version
     * @return {@link OrganizationMemberRole#OWNER} for an executable change, otherwise {@link OrganizationMemberRole#ADMIN}
     */
    public OrganizationMemberRole requiredRole(Boolean executableChange) {
        return Boolean.TRUE.equals(executableChange) ? OrganizationMemberRole.OWNER : OrganizationMemberRole.ADMIN;
    }

    /**
     * Why the user may not review the version, if they may not.
     *
     * @param userId  the reviewer
     * @param role    the reviewer's role in the version's organization
     * @param version the version
     * @param approve true for an approval, false for a rejection or a comment
     * @return {@link ReviewConstant#REASON_PROPOSER} or {@link ReviewConstant#REASON_ROLE}; empty when
     *         the review is allowed
     */
    public Optional<String> refusal(UUID userId, OrganizationMemberRole role, Version version, boolean approve) {
        OrganizationMemberRole minimum = approve ? requiredRole(version.getExecutableChange()) : OrganizationMemberRole.ADMIN;
        if (!role.isAtLeast(minimum)) {
            return Optional.of(ReviewConstant.REASON_ROLE);
        }
        if (userId.equals(version.getProposedBy())) {
            return Optional.of(ReviewConstant.REASON_PROPOSER);
        }
        return Optional.empty();
    }
}
