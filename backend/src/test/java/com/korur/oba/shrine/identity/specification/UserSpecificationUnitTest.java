package com.korur.oba.shrine.identity.specification;

// 1. Framework-level
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.dto.UserQueryRequest;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class UserSpecificationUnitTest {

    @Mock
    private Root<User> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path statusPath;

    @Mock
    private Path idPath;

    @Mock
    private Predicate predicate;

    @Mock
    private Predicate conjunction;

    @Mock
    private Predicate disjunction;

    @BeforeEach
    void setUp() {
        lenient().when(cb.conjunction()).thenReturn(conjunction);
        lenient().when(cb.disjunction()).thenReturn(disjunction);
        lenient().when(conjunction.not()).thenReturn(conjunction);
        lenient().when(cb.and(org.mockito.ArgumentMatchers.<Predicate>any(),
                org.mockito.ArgumentMatchers.<Predicate>any())).thenReturn(predicate);
        lenient().when(root.get("status")).thenReturn(statusPath);
        lenient().when(root.get("id")).thenReturn(idPath);
        lenient().when(statusPath.in(org.mockito.ArgumentMatchers.<java.util.Collection<?>>any())).thenReturn(predicate);
        lenient().when(idPath.in(org.mockito.ArgumentMatchers.<java.util.Collection<?>>any())).thenReturn(predicate);
    }

    @Test
    void visibleTo_emptySet_matchesNothing() {
        Specification<User> spec = UserSpecification.visibleTo(Set.of());

        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
        verify(cb).disjunction();
        verify(root, never()).get("id");
    }

    @Test
    void visibleTo_nullSet_matchesNothing() {
        Specification<User> spec = UserSpecification.visibleTo(null);

        spec.toPredicate(root, query, cb);

        verify(cb).disjunction();
    }

    @Test
    void visibleTo_idSet_restrictsOnId() {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        Specification<User> spec = UserSpecification.visibleTo(ids);

        spec.toPredicate(root, query, cb);

        verify(root).get("id");
        verify(idPath).in(ids);
        verify(cb, never()).disjunction();
    }

    @Test
    void filterBy_statusesGiven_filtersOnStatusField() {
        UserQueryRequest request = new UserQueryRequest();
        List<UserStatus> statuses = List.of(UserStatus.ACTIVE);
        request.setUserStatuses(statuses);
        Specification<User> spec = UserSpecification.filterBy(Set.of(UUID.randomUUID()), request);

        spec.toPredicate(root, query, cb);

        verify(root).get("status");
        verify(statusPath).in(statuses);
        verify(root, never()).get("userStatus");
    }

    @Test
    void filterBy_emptyVisibleSetAndNoFilters_stillMatchesNothing() {
        Specification<User> spec = UserSpecification.filterBy(Set.of(), new UserQueryRequest());

        spec.toPredicate(root, query, cb);

        verify(cb).disjunction();
        verify(root, never()).get(anyString());
    }

    @Test
    void filterBy_nullRequest_returnsVisibilityRestrictionOnly() {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        Specification<User> spec = UserSpecification.filterBy(ids, null);

        spec.toPredicate(root, query, cb);

        verify(idPath).in(ids);
    }
}
