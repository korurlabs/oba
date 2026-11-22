package com.korur.oba.shrine.document.service.template.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.service.BlobUrlRewriterMapper;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateQueryRequest;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateDetailResponse;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateListResponse;
import com.korur.oba.shrine.document.entity.template.DocumentTemplate;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;
import com.korur.oba.shrine.document.mapper.template.DocumentTemplateMapperImpl;
import com.korur.oba.shrine.document.repository.template.DocumentTemplateRepository;
import com.korur.oba.shrine.document.validator.template.DocumentTemplateValidator;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityListResponse;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;

@ExtendWith(MockitoExtension.class)
class DocumentTemplateQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private DocumentTemplateRepository documentTemplateRepository;

    @Mock
    private BlobUrlRewriterMapper blobUrlRewriterMapper;

    @Mock
    private LegalEntityQueryService legalEntityQueryService;

    @Mock
    private LoggingService loggingService;

    private DocumentTemplateQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentTemplateQueryServiceImpl(documentTemplateRepository,
                new DocumentTemplateMapperImpl(blobUrlRewriterMapper),
                new DocumentTemplateValidator(documentTemplateRepository, loggingService), legalEntityQueryService,
                loggingService);
    }

    @Test
    void list_pagesByNameLanguageIdAndMapsTheItems() {
        // Arrange
        DocumentTemplate template = DocumentTemplate.builder().id(UUID.randomUUID()).name("NDA")
                .type(DocumentType.NDA).language(DocumentLanguage.NL).build();
        when(documentTemplateRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(template)));
        DocumentTemplateQueryRequest request = new DocumentTemplateQueryRequest();
        request.setPage(2);
        request.setAmount(10);

        // Act
        Page<DocumentTemplateListResponse> result = service.list(organizationId, request);

        // Assert
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(documentTemplateRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort().stream().map(order -> order.getProperty()))
                .containsExactly("name", "language", "id");
        assertThat(result.getContent()).singleElement().satisfies((DocumentTemplateListResponse item) -> {
            assertThat(item.name()).isEqualTo("NDA");
            assertThat(item.ready()).isFalse();
            assertThat(item.legalEntityId()).isNull();
            assertThat(item.legalEntityShortName()).isNull();
        });
        verifyNoInteractions(legalEntityQueryService);
    }

    @Test
    void list_rowsWithLegalEntity_carryItsShortNameFromOneLookup() {
        // Arrange
        UUID legalEntityId = UUID.randomUUID();
        DocumentTemplate own = DocumentTemplate.builder().id(UUID.randomUUID()).name("Contract")
                .type(DocumentType.CONTRACT).language(DocumentLanguage.EN).legalEntityId(legalEntityId).build();
        DocumentTemplate shared = DocumentTemplate.builder().id(UUID.randomUUID()).name("NDA")
                .type(DocumentType.NDA).language(DocumentLanguage.EN).build();
        when(documentTemplateRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(own, shared)));
        LegalEntityListResponse legalEntity = mock(LegalEntityListResponse.class);
        when(legalEntity.id()).thenReturn(legalEntityId);
        when(legalEntity.shortName()).thenReturn("Korur B.V.");
        when(legalEntityQueryService.list(organizationId)).thenReturn(List.of(legalEntity));
        DocumentTemplateQueryRequest request = new DocumentTemplateQueryRequest();
        request.setLegalEntityId(legalEntityId);

        // Act
        Page<DocumentTemplateListResponse> result = service.list(organizationId, request);

        // Assert
        assertThat(result.getContent()).extracting(DocumentTemplateListResponse::legalEntityShortName)
                .containsExactly("Korur B.V.", null);
        assertThat(result.getContent()).extracting(DocumentTemplateListResponse::legalEntityId)
                .containsExactly(legalEntityId, null);
        verify(legalEntityQueryService).list(organizationId);
    }

    @Test
    void get_templateOfOrganization_returnsDetailWithSignedUrls() {
        // Arrange
        UUID templateId = UUID.randomUUID();
        DocumentTemplate template = DocumentTemplate.builder().id(templateId).name("NDA").type(DocumentType.NDA)
                .language(DocumentLanguage.NL).htmlBlobUrl("h").pdfBlobUrl("p").build();
        when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                .thenReturn(Optional.of(template));
        when(blobUrlRewriterMapper.rewriteBlobUrl("h")).thenReturn("signed-h");
        when(blobUrlRewriterMapper.rewriteBlobUrl("p")).thenReturn("signed-p");

        // Act
        DocumentTemplateDetailResponse detail = service.get(organizationId, templateId);

        // Assert
        assertThat(detail.htmlUrl()).isEqualTo("signed-h");
        assertThat(detail.pdfUrl()).isEqualTo("signed-p");
        assertThat(detail.ready()).isTrue();
        assertThat(detail.legalEntityShortName()).isNull();
        verifyNoInteractions(legalEntityQueryService);
    }

    @Test
    void get_templateWithLegalEntity_returnsItsShortName() {
        // Arrange
        UUID templateId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();
        DocumentTemplate template = DocumentTemplate.builder().id(templateId).name("Contract")
                .type(DocumentType.CONTRACT).language(DocumentLanguage.EN).legalEntityId(legalEntityId).build();
        when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                .thenReturn(Optional.of(template));
        when(legalEntityQueryService.getEntity(organizationId, legalEntityId))
                .thenReturn(LegalEntity.builder().shortName("Korur B.V.").build());

        // Act
        DocumentTemplateDetailResponse detail = service.get(organizationId, templateId);

        // Assert
        assertThat(detail.legalEntityId()).isEqualTo(legalEntityId);
        assertThat(detail.legalEntityShortName()).isEqualTo("Korur B.V.");
    }

    @Test
    void get_templateOfAnotherOrganization_throwsNotFound() {
        // Arrange
        UUID templateId = UUID.randomUUID();
        when(documentTemplateRepository.findByIdAndOrganizationId(eq(templateId), eq(organizationId)))
                .thenReturn(Optional.empty());

        // Act and assert
        assertThatThrownBy(() -> service.get(organizationId, templateId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getEntity_templateOfAnotherOrganization_throwsNotFound() {
        // Arrange
        UUID templateId = UUID.randomUUID();
        when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                .thenReturn(Optional.empty());

        // Act and assert
        assertThatThrownBy(() -> service.getEntity(organizationId, templateId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getEntity_templateOfOrganization_returnsTheEntity() {
        // Arrange
        UUID templateId = UUID.randomUUID();
        DocumentTemplate template = DocumentTemplate.builder().id(templateId).build();
        when(documentTemplateRepository.findByIdAndOrganizationId(templateId, organizationId))
                .thenReturn(Optional.of(template));

        // Act and assert
        assertThat(service.getEntity(organizationId, templateId)).isSameAs(template);
    }
}
