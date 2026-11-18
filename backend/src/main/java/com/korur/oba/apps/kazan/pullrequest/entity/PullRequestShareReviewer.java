package com.korur.oba.apps.kazan.pullrequest.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Entity representing one member the author asked to review a shared pull
 * request, and whether that member approved it in Kazan.
 *
 * A user is a reviewer of a share at most once. The approval lives only in
 * Kazan and is never written to GitHub.
 */
@Entity
@Table(name = "pull_request_share_reviewers", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PullRequestShareReviewer extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Zero-based place of the reviewer in the share's list.
     */
    @Column(name = "position", nullable = false)
    private Integer position;

    /**
     * Time the reviewer approved; null while not approved, and cleared again
     * when the reviewer withdraws the approval.
     */
    @Column(name = "approved_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = true)
    private Instant approvedAt;

    // -------------Relations-----------//

    /**
     * Share this reviewer belongs to.
     *
     * No cascade -- the row is owned by PullRequestShare.reviewers.
     *
     * Fetch: EAGER -- Hibernate 6 does not support a LAZY to-one association to
     * an entity that uses @SoftDelete.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "share_id", nullable = false, updatable = false)
    private PullRequestShare share;

    /**
     * Member asked to review.
     *
     * No cascade -- user lifecycle is independent of the share.
     *
     * Fetch: EAGER -- Hibernate 6 does not support a LAZY to-one association to
     * an entity that uses @SoftDelete.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;
}
