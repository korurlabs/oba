package com.korur.oba.apps.kaizen.pullrequest.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShareReviewer;

/**
 * Repository interface for PullRequestShareReviewer entity database operations.
 *
 * Rows are created and removed through PullRequestShare.reviewers; this
 * repository only reads the row of one reviewer, for an approval or a
 * withdrawal.
 */
@Repository
public interface PullRequestShareReviewerRepository extends JpaRepository<PullRequestShareReviewer, UUID>,
        JpaSpecificationExecutor<PullRequestShareReviewer> {

    /**
     * Finds the reviewer row of one user on one share.
     *
     * @param shareId ID of the share
     * @param userId  ID of the user
     * @return the row; empty when the user is not a reviewer of the share
     */
    Optional<PullRequestShareReviewer> findByShareIdAndUserId(UUID shareId, UUID userId);
}
