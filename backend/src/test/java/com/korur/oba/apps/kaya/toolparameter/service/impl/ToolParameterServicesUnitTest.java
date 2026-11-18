package com.korur.oba.apps.kaya.toolparameter.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party
import org.mapstruct.factory.Mappers;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.toolparameter.dto.embedded.ToolParameterIndex;
import com.korur.oba.apps.kaya.toolparameter.dto.response.ToolParameterDetailResponse;
import com.korur.oba.apps.kaya.toolparameter.entity.ToolParameter;
import com.korur.oba.apps.kaya.toolparameter.enums.ToolParameterFormat;
import com.korur.oba.apps.kaya.toolparameter.mapper.ToolParameterMapper;
import com.korur.oba.apps.kaya.toolparameter.repository.ToolParameterRepository;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolParameterServicesUnitTest {

    @Mock
    private ToolParameterRepository toolParameterRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private ToolParameterQueryServiceImpl toolParameterQueryService;
    private ToolParameterServiceImpl toolParameterService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        toolParameterQueryService = new ToolParameterQueryServiceImpl(toolParameterRepository, blobQueryService,
                Mappers.getMapper(ToolParameterMapper.class), loggingService);
        toolParameterService = new ToolParameterServiceImpl(toolParameterRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    @Test
    void getToolParameter_fileOfOrganization_returnsFormatAndContent() {
        ToolParameter toolParameter = ToolParameter.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .name("openai.yml")
                .format(ToolParameterFormat.YAML)
                .build();
        byte[] content = "interface:\n  display_name: Kaizen\n".getBytes(StandardCharsets.UTF_8);
        Blob blob = Blob.builder().id(toolParameter.getContentBlobId()).content(content).size(content.length).build();
        when(toolParameterRepository.findByIdAndOrganizationId(toolParameter.getId(), organizationId))
                .thenReturn(Optional.of(toolParameter));
        when(blobQueryService.getEntityById(organizationId, toolParameter.getContentBlobId())).thenReturn(blob);

        ToolParameterDetailResponse result = toolParameterQueryService.getToolParameter(organizationId,
                toolParameter.getId());

        assertEquals("interface:\n  display_name: Kaizen\n", result.content());
        assertEquals(ToolParameterFormat.YAML, result.format());
        assertEquals("openai.yml", result.name());
        assertEquals(content.length, result.sizeBytes());
    }

    @Test
    void getToolParameter_unknownOrOtherOrganization_throwsNotFound() {
        UUID toolParameterId = UUID.randomUUID();
        when(toolParameterRepository.findByIdAndOrganizationId(toolParameterId, organizationId))
                .thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> toolParameterQueryService.getToolParameter(organizationId, toolParameterId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_indexRows_deletesFirstAndWritesInGivenOrder() {
        ToolParameterIndex json = new ToolParameterIndex("evals.json", UUID.randomUUID(), ToolParameterFormat.JSON);
        ToolParameterIndex yaml = new ToolParameterIndex("openai.yml", UUID.randomUUID(), ToolParameterFormat.YAML);
        when(toolParameterRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ToolParameter> result = toolParameterService.replaceVersion(organizationId, versionId, List.of(json, yaml));

        InOrder order = inOrder(toolParameterRepository);
        order.verify(toolParameterRepository).deleteByVersion(organizationId, versionId);
        order.verify(toolParameterRepository).saveAll(any(List.class));
        assertEquals(2, result.size());
        assertEquals(ToolParameterFormat.JSON, result.get(0).getFormat());
        assertEquals("openai.yml", result.get(1).getName());
        assertEquals(ToolParameterFormat.YAML, result.get(1).getFormat());
        assertEquals(organizationId, result.get(1).getOrganizationId());
        assertEquals(versionId, result.get(1).getVersionId());
        assertEquals(yaml.contentBlobId(), result.get(1).getContentBlobId());
    }

    @Test
    void savePart_index_addsOneRowWithoutDeleting() {
        UUID blobId = UUID.randomUUID();
        when(toolParameterRepository.save(any(ToolParameter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ToolParameter result = toolParameterService.savePart(organizationId, versionId, new ToolParameterIndex("openai.yml", blobId, ToolParameterFormat.YAML));

        assertEquals(blobId, result.getContentBlobId());
        verify(toolParameterRepository, never()).deleteByVersion(any(), any());
    }

    @Test
    void deleteParts_noIds_deletesNothing() {
        assertEquals(0, toolParameterService.deleteParts(organizationId, versionId, List.of()));

        verify(toolParameterRepository, never()).deleteParts(any(), any(), anyCollection());
    }

    @Test
    void deleteParts_ids_deletesThem() {
        List<UUID> partIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(toolParameterRepository.deleteParts(organizationId, versionId, partIds)).thenReturn(2);

        assertEquals(2, toolParameterService.deleteParts(organizationId, versionId, partIds));
    }
}
