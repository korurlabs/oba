package com.korur.oba.shrine.document.service.document.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.document.dto.request.document.DocumentQueryRequest;
import com.korur.oba.shrine.document.entity.document.Document;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;
import com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError;
import com.korur.oba.shrine.document.mapper.document.DocumentMapperHelper;
import com.korur.oba.shrine.document.repository.document.DocumentRepository;
import com.korur.oba.shrine.document.validator.document.DocumentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;

@ExtendWith(MockitoExtension.class)
class DocumentQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentMapperHelper documentMapperHelper;

    @Mock
    private ResourceAccessQueryService resourceAccessQueryService;

    @Mock
    private OrganizationAccessService organizationAccessService;

    @Mock
    private LoggingService loggingService;

    private DocumentQueryServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new DocumentQueryServiceImpl(documentRepository,
                new DocumentValidator(documentRepository, loggingService), documentMapperHelper,
                resourceAccessQueryService, organizationAccessService, loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @SuppressWarnings("unchecked")
    private void listsAnEmptyPage() {
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<Document>(List.of(), invocation.getArgument(1), 0));
        when(documentMapperHelper.toListResponses(eq(organizationId), anyList())).thenReturn(List.of());
    }

    @Nested
    class ListDocuments {

        @Test
        void withRelationFilter_checksReadOnThatRelationOnly() {
            // Arrange
            UUID relationId = UUID.randomUUID();
            DocumentQueryRequest request = new DocumentQueryRequest();
            request.setRelationId(relationId);
            listsAnEmptyPage();

            // Act
            service.list(user, organizationId, request);

            // Assert
            verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId, ResourceAccessType.RELATION,
                    relationId, ResourceAccessAction.READ);
            verifyNoInteractions(organizationAccessService);
        }

        @Test
        void withoutRelationFilter_checksOtakAccessOnceAndSortsByLastChange() {
            // Arrange
            DocumentQueryRequest request = new DocumentQueryRequest();
            listsAnEmptyPage();

            // Act
            Page<?> page = service.list(user, organizationId, request);

            // Assert
            verify(organizationAccessService).verifyOrganizationAccess(organizationId, user.getId(),
                    ResourceAccessType.RELATION.minimumRole(ResourceAccessAction.READ), App.OTAK);
            verifyNoInteractions(resourceAccessQueryService);
            Sort.Order first = page.getPageable().getSort().getOrderFor("updatedAt");
            assertThat(first).isNotNull();
            assertThat(first.getDirection()).isEqualTo(Sort.Direction.DESC);
            assertThat(page.getPageable().getPageNumber()).isZero();
        }
    }

    @Nested
    class Get {

        @Test
        void documentOfTheOrganization_checksReadOnItsOwner() {
            // Arrange
            UUID documentId = UUID.randomUUID();
            UUID relationId = UUID.randomUUID();
            Document document = Document.builder().id(documentId).entityType(DocumentEntityType.RELATION)
                    .entityId(relationId).build();
            when(documentRepository.findByIdAndOrganizationId(documentId, organizationId))
                    .thenReturn(Optional.of(document));

            // Act
            service.get(user, organizationId, documentId);

            // Assert
            verify(resourceAccessQueryService).verifyResourceAccess(user, organizationId, ResourceAccessType.RELATION,
                    relationId, ResourceAccessAction.READ);
            verify(documentMapperHelper).toDetailResponse(organizationId, document);
        }

        @Test
        void documentOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID documentId = UUID.randomUUID();
            when(documentRepository.findByIdAndOrganizationId(documentId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.get(user, organizationId, documentId))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainDocumentError.NOT_DOCUMENT_FOUND.getCode());
            verifyNoInteractions(resourceAccessQueryService, documentMapperHelper);
        }
    }
}
