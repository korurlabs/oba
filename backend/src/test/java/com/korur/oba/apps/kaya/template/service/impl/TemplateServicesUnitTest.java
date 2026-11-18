package com.korur.oba.apps.kaya.template.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import org.mapstruct.factory.Mappers;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.template.dto.embedded.TemplateIndex;
import com.korur.oba.apps.kaya.template.dto.request.TemplateQueryRequest;
import com.korur.oba.apps.kaya.template.dto.response.TemplateDetailResponse;
import com.korur.oba.apps.kaya.template.dto.response.TemplateListResponse;
import com.korur.oba.apps.kaya.template.entity.Template;
import com.korur.oba.apps.kaya.template.mapper.TemplateMapper;
import com.korur.oba.apps.kaya.template.repository.TemplateRepository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServicesUnitTest {

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private TemplateQueryServiceImpl templateQueryService;
    private TemplateServiceImpl templateService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        templateQueryService = new TemplateQueryServiceImpl(templateRepository, blobQueryService,
                Mappers.getMapper(TemplateMapper.class), loggingService);
        templateService = new TemplateServiceImpl(templateRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    private Template template(String relativePath) {
        return Template.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .relativePath(relativePath)
                .title("Handoff template")
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTemplates_templatesFound_returnsPathsAndSizes() {
        Template first = template("handoff.md");
        Template second = template("spec-template.md");
        TemplateQueryRequest request = new TemplateQueryRequest();
        request.setVersionId(List.of(versionId));
        when(templateRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(first, second));
        when(blobQueryService.getSizesByIds(organizationId, Set.of(first.getContentBlobId(), second.getContentBlobId())))
                .thenReturn(Map.of(first.getContentBlobId(), 900, second.getContentBlobId(), 50));

        List<TemplateListResponse> result = templateQueryService.getTemplates(organizationId, request);

        assertEquals(2, result.size());
        assertEquals("handoff.md", result.get(0).path());
        assertEquals("Handoff template", result.get(0).title());
        assertEquals(900, result.get(0).sizeBytes());
        assertEquals("spec-template.md", result.get(1).path());
        assertEquals(50, result.get(1).sizeBytes());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTemplates_noTemplates_readsNoSizes() {
        TemplateQueryRequest request = new TemplateQueryRequest();
        request.setVersionId(List.of(versionId));
        when(templateRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<TemplateListResponse> result = templateQueryService.getTemplates(organizationId, request);

        assertEquals(0, result.size());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    void getTemplate_templateOfOrganization_returnsContentAsUtf8() {
        Template template = template("handoff.md");
        byte[] content = "---\ntitle: Handoff template\n---\n# Handoff template\n".getBytes(StandardCharsets.UTF_8);
        Blob blob = Blob.builder().id(template.getContentBlobId()).content(content).size(content.length).build();
        when(templateRepository.findByIdAndOrganizationId(template.getId(), organizationId)).thenReturn(Optional.of(template));
        when(blobQueryService.getEntityById(organizationId, template.getContentBlobId())).thenReturn(blob);

        TemplateDetailResponse result = templateQueryService.getTemplate(organizationId, template.getId());

        assertEquals("---\ntitle: Handoff template\n---\n# Handoff template\n", result.content());
        assertEquals("handoff.md", result.path());
        assertEquals(content.length, result.sizeBytes());
    }

    @Test
    void getTemplate_unknownOrOtherOrganization_throwsNotFound() {
        UUID templateId = UUID.randomUUID();
        when(templateRepository.findByIdAndOrganizationId(templateId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> templateQueryService.getTemplate(organizationId, templateId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_indexRows_deletesFirstAndWritesTemplates() {
        UUID blobId = UUID.randomUUID();
        TemplateIndex index = new TemplateIndex("handoff.md", blobId, "Handoff template");
        when(templateRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Template> result = templateService.replaceVersion(organizationId, versionId, List.of(index));

        InOrder order = inOrder(templateRepository);
        order.verify(templateRepository).deleteByVersion(organizationId, versionId);
        order.verify(templateRepository).saveAll(any(List.class));
        assertEquals(1, result.size());
        assertEquals(organizationId, result.get(0).getOrganizationId());
        assertEquals(versionId, result.get(0).getVersionId());
        assertEquals(blobId, result.get(0).getContentBlobId());
        assertEquals("handoff.md", result.get(0).getRelativePath());
        assertEquals("Handoff template", result.get(0).getTitle());
    }

    @Test
    void saveFile_index_deletesTheOldRowFirstAndWritesOne() {
        UUID blobId = UUID.randomUUID();
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Template result = templateService.saveFile(organizationId, versionId, new TemplateIndex("spec.md", blobId, "Spec"));

        InOrder order = inOrder(templateRepository);
        order.verify(templateRepository).deleteByPath(organizationId, versionId, "spec.md");
        order.verify(templateRepository).save(any(Template.class));
        assertEquals(organizationId, result.getOrganizationId());
        assertEquals(versionId, result.getVersionId());
        assertEquals(blobId, result.getContentBlobId());
    }

    @Test
    void deleteFile_noRowAtPath_returnsFalse() {
        when(templateRepository.deleteByPath(organizationId, versionId, "spec.md")).thenReturn(0);

        assertFalse(templateService.deleteFile(organizationId, versionId, "spec.md"));
    }

    @Test
    void deleteFile_rowAtPath_returnsTrue() {
        when(templateRepository.deleteByPath(organizationId, versionId, "spec.md")).thenReturn(1);

        assertTrue(templateService.deleteFile(organizationId, versionId, "spec.md"));
    }
}
