package com.korur.oba.apps.kazan.pullrequest.specification;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 2. Third-party
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareQueryRequest;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kazan.pullrequest.enums.PullRequestShareStatus;

/**
 * Checks the shape of the criteria each filter builds against a mocked Criteria API: no PostgreSQL
 * runs here, so the row-level behaviour is covered by the predicates the filters ask the builder for.
 */
class PullRequestShareSpecificationUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID readerId = UUID.randomUUID();

    private Root<PullRequestShare> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        root = mock(Root.class, RETURNS_DEEP_STUBS);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        when(cb.conjunction()).thenReturn(mock(Predicate.class));
        when(cb.disjunction()).thenReturn(mock(Predicate.class));
    }

    private void apply(Specification<PullRequestShare> specification) {
        specification.toPredicate(root, query, cb);
    }

    @Nested
    class Organization {

        @Test
        void nullOrganization_matchesNothingAndAppliesNoFilter() {
            // Act
            apply(PullRequestShareSpecification.filterBy(null, readerId, new PullRequestShareQueryRequest()));

            // Assert
            verify(cb).disjunction();
            verify(cb, never()).equal(any(), any(Object.class));
        }

        @Test
        void organization_keepsOnlyItsShares() {
            // Arrange
            Path<Object> organizationPath = root.get("organizationId");

            // Act
            apply(PullRequestShareSpecification.filterBy(organizationId, readerId,
                    new PullRequestShareQueryRequest()));

            // Assert
            verify(cb).equal(organizationPath, organizationId);
            verify(cb, never()).disjunction();
        }

        @Test
        void nullRequest_keepsOnlyTheOrganization() {
            // Arrange
            Path<Object> organizationPath = root.get("organizationId");

            // Act
            apply(PullRequestShareSpecification.filterBy(organizationId, readerId, null));

            // Assert
            verify(cb).equal(organizationPath, organizationId);
            verify(cb, never()).conjunction();
        }
    }

    @Nested
    class StatusAndAuthorFilters {

        @Test
        void emptyFilters_areNoOps() {
            // Arrange
            PullRequestShareQueryRequest request = new PullRequestShareQueryRequest();
            request.setStatuses(List.of());
            request.setAuthorIds(null);
            request.setAwaitingMyReview(null);

            // Act
            apply(PullRequestShareSpecification.filterBy(organizationId, readerId, request));

            // Assert
            verify(cb, times(3)).conjunction();
            verify(root, never()).join(any(String.class));
            verify(query, never()).distinct(true);
        }

        @Test
        void statuses_matchAnyOfThem() {
            // Arrange
            List<PullRequestShareStatus> statuses = List.of(PullRequestShareStatus.OPEN,
                    PullRequestShareStatus.DRAFT);
            PullRequestShareQueryRequest request = new PullRequestShareQueryRequest();
            request.setStatuses(statuses);
            Path<Object> statusPath = root.get("status");

            // Act
            apply(PullRequestShareSpecification.filterBy(organizationId, readerId, request));

            // Assert
            verify(statusPath).in(statuses);
        }

        @Test
        void authorIds_matchAnyOfThem() {
            // Arrange
            List<UUID> authorIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            PullRequestShareQueryRequest request = new PullRequestShareQueryRequest();
            request.setAuthorIds(authorIds);
            Path<Object> authorIdPath = root.get("author").get("id");

            // Act
            apply(PullRequestShareSpecification.filterBy(organizationId, readerId, request));

            // Assert
            verify(authorIdPath).in(authorIds);
        }
    }

    @Nested
    class AwaitingMyReview {

        @Test
        @SuppressWarnings("unchecked")
        void flagTrue_joinsTheReaderRowWithoutApprovalAndExcludesMerged() {
            // Arrange
            PullRequestShareQueryRequest request = new PullRequestShareQueryRequest();
            request.setAwaitingMyReview(true);
            Join<Object, Object> reviewers = mock(Join.class, RETURNS_DEEP_STUBS);
            when(root.join("reviewers")).thenReturn((Join) reviewers);
            Path<Object> reviewerUserId = reviewers.get("user").get("id");
            Path<Object> approvedAt = reviewers.get("approvedAt");
            Path<Object> status = root.get("status");

            // Act
            apply(PullRequestShareSpecification.filterBy(organizationId, readerId, request));

            // Assert
            verify(cb).equal(reviewerUserId, readerId);
            verify(cb).isNull(approvedAt);
            verify(cb).notEqual(status, PullRequestShareStatus.MERGED);
            verify(query).distinct(true);
        }

        @Test
        void flagTrueWithoutReader_matchesNothing() {
            // Arrange
            PullRequestShareQueryRequest request = new PullRequestShareQueryRequest();
            request.setAwaitingMyReview(true);

            // Act
            apply(PullRequestShareSpecification.filterBy(organizationId, null, request));

            // Assert
            verify(cb).disjunction();
            verify(root, never()).join(any(String.class));
        }

        @Test
        void flagFalse_isANoOp() {
            // Arrange
            PullRequestShareQueryRequest request = new PullRequestShareQueryRequest();
            request.setAwaitingMyReview(false);

            // Act
            apply(PullRequestShareSpecification.filterBy(organizationId, readerId, request));

            // Assert
            verify(root, never()).join(any(String.class));
            verify(query, never()).distinct(true);
        }
    }
}
