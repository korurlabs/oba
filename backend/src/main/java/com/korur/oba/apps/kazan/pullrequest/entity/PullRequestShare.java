package com.korur.oba.apps.kazan.pullrequest.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.pullrequest.enums.PullRequestShareStatus;
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Entity representing a GitHub pull request that a member shared in the
 * organization's pull requests chat channel, with the status, note and
 * reviewers the author chose.
 *
 * The GitHub facts (repository, number, title, GitHub author, branches and the
 * ticket key found in them) are a snapshot taken from GitHub when the pull
 * request is shared; they never come from the client and never follow GitHub
 * afterwards. The related tickets are the author's own list, separate from
 * the ticket key found on GitHub. The link is not stored: it is built from repositoryFullName and
 * number, so a card can only point at github.com. The author deletes a share
 * softly, as a daily update.
 */
@Entity
@Table(name = "pull_request_shares", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class PullRequestShare extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    /**
     * Organization whose pull requests channel shows the share; every read is
     * scoped to it.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * GitHub's numeric ID of the repository, which stays the same when the
     * repository is renamed.
     */
    @Column(name = "github_repository_id", nullable = false, updatable = false)
    private Long githubRepositoryId;

    /**
     * Repository as "owner/name" at share time.
     */
    @Column(name = "repository_full_name", length = 200, nullable = false, updatable = false)
    private String repositoryFullName;

    /**
     * Pull request number within the repository, always above zero.
     */
    @Column(name = "number", nullable = false, updatable = false)
    private Integer number;

    /**
     * Title at share time, cut to 500 characters.
     */
    @Column(name = "title", length = 500, nullable = false, updatable = false)
    private String title;

    /**
     * GitHub login of the pull request's author, who need not be a Kazan user.
     */
    @Column(name = "github_author_login", length = 100, nullable = false, updatable = false)
    private String githubAuthorLogin;

    /**
     * Branch the pull request merges from.
     */
    @Column(name = "head_ref", length = 255, nullable = false, updatable = false)
    private String headRef;

    /**
     * Branch the pull request merges into.
     */
    @Column(name = "base_ref", length = 255, nullable = false, updatable = false)
    private String baseRef;

    /**
     * Upper-case ticket key found in the head branch, else in the title; null
     * when neither holds one. Plain text, not a relation: whether a reader may
     * open the ticket is decided per reader at read time.
     */
    @Column(name = "ticket_key", length = 50, nullable = true, updatable = false)
    private String ticketKey;

    /**
     * Review state the author chose; edited by the author only.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private PullRequestShareStatus status;

    /**
     * Optional plain-text note shown after the status; null when left empty.
     */
    @Column(name = "note", length = 500, nullable = true)
    private String note;

    /**
     * Time of the author's last edit; null until the first edit. An approval
     * or a withdrawal never sets it, so "Edited" shows only the author's changes.
     */
    @Column(name = "edited_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = true)
    private Instant editedAt;

    // -------------Relations-----------//

    /**
     * Member who shared the pull request; only this user may edit or delete
     * the share.
     *
     * No cascade -- user lifecycle is independent of the share.
     *
     * Fetch: EAGER -- required by Hibernate 6 when both source and target entities use @SoftDelete.
     * LAZY is not supported in this configuration.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    /**
     * Reviewers in the order the author chose them, each with their approval.
     *
     * Cascade: ALL + orphanRemoval -- a reviewer row exists only inside its
     * share, so an edit adds and removes reviewers through this collection. A
     * kept reviewer is changed in place and keeps its approval: removing and
     * re-adding the row for the same user would insert before the orphan
     * delete and break the unique key on (share_id, user_id).
     *
     * Batch size: the reviewers of a whole chat page load in one query when
     * the first share's reviewers are read, instead of one query per share.
     */
    @Builder.Default
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "share", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PullRequestShareReviewer> reviewers = new ArrayList<>();

    /**
     * Tickets the author listed as related, in the order the author chose,
     * each a snapshot taken when it was added.
     *
     * Cascade: ALL + orphanRemoval -- a ticket row exists only inside its
     * share, so an edit adds and removes tickets through this collection. A
     * kept ticket keeps its row and snapshot and only changes position:
     * removing and re-adding the row for the same ticket would insert before
     * the orphan delete and break the unique key on (share_id, ticket_id).
     *
     * Batch size: the tickets of a whole chat page load in one query when the
     * first share's tickets are read, instead of one query per share.
     */
    @Builder.Default
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "share", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PullRequestShareTicket> relatedTickets = new ArrayList<>();
}
