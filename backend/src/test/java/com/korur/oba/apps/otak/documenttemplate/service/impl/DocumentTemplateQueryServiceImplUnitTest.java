package com.korur.oba.apps.otak.documenttemplate.service.impl;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.dto.request.DocumentTemplateQueryRequest;
import com.korur.oba.apps.otak.documenttemplate.dto.response.DocumentTemplateDetailResponse;
import com.korur.oba.apps.otak.documenttemplate.dto.response.DocumentTemplateListResponse;
import com.korur.oba.apps.otak.documenttemplate.entity.DocumentTemplate;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentLanguage;
import com.korur.oba.apps.otak.documenttemplate.enums.DocumentType;
import com.korur.oba.apps.otak.documenttemplate.mapper.DocumentTemplateMapperImpl;
import com.korur.oba.apps.otak.documenttemplate.repository.DocumentTemplateRepository;
import com.korur.oba.apps.otak.documenttemplate.validator.DocumentTemplateValidator;
import com.korur.oba.connect.azure.service.BlobUrlRewriterMapper;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class DocumentTemplateQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private DocumentTemplateRepository documentTemplateRepository;

    @Mock
    private BlobUrlRewriterMapper blobUrlRewriterMapper;

    @Mock
    private LoggingService loggingService;

    private DocumentTemplateQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentTemplateQueryServiceImpl(documentTemplateRepository,
                new DocumentTemplateMapperImpl(blobUrlRewriterMapper),
                new DocumentTemplateValidator(documentTemplateRepository, loggingService), loggingService);
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
        });
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
