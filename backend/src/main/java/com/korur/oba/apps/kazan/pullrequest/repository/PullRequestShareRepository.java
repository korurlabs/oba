package com.korur.oba.apps.kazan.pullrequest.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for PullRequestShare entity database operations.
 *
 * Every read is scoped to one organization, so a share is never read outside
 * the organization whose channel shows it. Queries exclude soft-deleted shares
 * through the Hibernate soft-delete filter. Reviewers are saved and removed
 * through PullRequestShare.reviewers.
 */
@Repository
public interface PullRequestShareRepository extends SoftDeleteRepository<PullRequestShare, UUID>,
        JpaSpecificationExecutor<PullRequestShare> {

    /**
     * Finds a live share of the organization.
     *
     * @param id             ID of the share
     * @param organizationId organization the caller works in
     * @return the share; empty when absent, deleted or of another organization
     */
    Optional<PullRequestShare> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Finds the live shares of the organization among the given IDs, for the
     * share entries of one chat page.
     *
     * @param ids            IDs of the shares, not empty
     * @param organizationId organization of the chat channel
     * @return the shares found, in no particular order
     */
    List<PullRequestShare> findAllByIdInAndOrganizationId(Collection<UUID> ids, UUID organizationId);
}
