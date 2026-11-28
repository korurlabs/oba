package com.korur.oba.apps.otak.relation.specification;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 2. Third-party
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.apps.otak.relation.dto.request.RelationQueryRequest;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;

/**
 * Checks the criteria the type and stage filters of the relations list build, against a mocked
 * Criteria API: no PostgreSQL runs here. The type and stage filters are independent, so a stage
 * filter applies without a type, and with no stage the inactive leads are left out whatever the
 * type filter.
 */
class RelationSpecificationUnitTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    private Root<Relation> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Path<Object> typePath;
    private Path<Object> stagePath;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        typePath = mock(Path.class, RETURNS_DEEP_STUBS);
        stagePath = mock(Path.class, RETURNS_DEEP_STUBS);
        when(root.get(RelationConstant.FIELD_TYPE)).thenReturn(typePath);
        when(root.get(RelationConstant.FIELD_STAGE)).thenReturn(stagePath);
    }

    private void apply(RelationQueryRequest request) {
        Specification<Relation> specification = RelationSpecification.filterBy(ORGANIZATION_ID, request);
        specification.toPredicate(root, query, cb);
    }

    @Test
    void filterBy_severalTypes_matchesTypeInThoseTypes() {
        // Arrange
        RelationQueryRequest request = new RelationQueryRequest();
        request.setType(List.of(RelationType.LEAD, RelationType.SUPPLIER));

        // Act
        apply(request);

        // Assert
        verify(typePath).in(List.of(RelationType.LEAD, RelationType.SUPPLIER));
    }

    @Test
    void filterBy_noType_addsNoTypeFilter() {
        // Arrange
        RelationQueryRequest request = new RelationQueryRequest();

        // Act
        apply(request);

        // Assert
        verify(typePath, never()).in(anyCollection());
    }

    @Test
    void filterBy_emptyTypeList_addsNoTypeFilter() {
        // Arrange
        RelationQueryRequest request = new RelationQueryRequest();
        request.setType(List.of());

        // Act
        apply(request);

        // Assert
        verify(typePath, never()).in(anyCollection());
    }

    @Test
    void filterBy_noStage_leavesOutOnlyLeadsAtInactiveStages() {
        // Arrange
        RelationQueryRequest request = new RelationQueryRequest();

        // Act
        apply(request);

        // Assert
        verify(cb).equal(typePath, RelationType.LEAD);
        verify(stagePath).in(PipelineStage.INACTIVE_LEAD_STAGES);
        verify(cb).not(any());
    }

    @Test
    void filterBy_noStageWithCustomerAndSupplierTypes_stillLeavesOutOnlyInactiveLeads() {
        // Arrange
        RelationQueryRequest request = new RelationQueryRequest();
        request.setType(List.of(RelationType.CUSTOMER, RelationType.SUPPLIER));

        // Act
        apply(request);

        // Assert
        verify(typePath).in(List.of(RelationType.CUSTOMER, RelationType.SUPPLIER));
        verify(cb).equal(typePath, RelationType.LEAD);
        verify(stagePath).in(PipelineStage.INACTIVE_LEAD_STAGES);
        verify(cb).not(any());
    }

    @Test
    void filterBy_stagesWithoutType_matchesStageInThoseStagesAndKeepsInactiveLeadsNamed() {
        // Arrange
        RelationQueryRequest request = new RelationQueryRequest();
        request.setStage(List.of(PipelineStage.LOST, PipelineStage.DIRECT));

        // Act
        apply(request);

        // Assert
        verify(stagePath).in(List.of(PipelineStage.LOST, PipelineStage.DIRECT));
        verify(stagePath, never()).in(PipelineStage.INACTIVE_LEAD_STAGES);
        verify(cb, never()).equal(typePath, RelationType.LEAD);
        verify(cb, never()).not(any());
        verify(typePath, never()).in(anyCollection());
    }

    @Test
    void filterBy_emptyStageList_actsAsNoStage() {
        // Arrange
        RelationQueryRequest request = new RelationQueryRequest();
        request.setStage(List.of());

        // Act
        apply(request);

        // Assert
        verify(stagePath).in(PipelineStage.INACTIVE_LEAD_STAGES);
        verify(cb).not(any());
    }

    @Test
    void filterBy_anyFilters_alwaysScopesToTheOrganization() {
        // Arrange
        RelationQueryRequest request = new RelationQueryRequest();
        request.setType(List.of(RelationType.SUPPLIER));
        request.setStage(List.of(PipelineStage.NEW));

        // Act
        apply(request);

        // Assert
        verify(cb).equal(root.get(RelationConstant.FIELD_ORGANIZATION_ID), ORGANIZATION_ID);
    }
}
