package com.korur.oba.apps.kaya.script.service.impl;

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
import com.korur.oba.apps.kaya.script.dto.embedded.ScriptIndex;
import com.korur.oba.apps.kaya.script.dto.response.ScriptDetailResponse;
import com.korur.oba.apps.kaya.script.entity.Script;
import com.korur.oba.apps.kaya.script.mapper.ScriptMapper;
import com.korur.oba.apps.kaya.script.repository.ScriptRepository;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScriptServicesUnitTest {

    @Mock
    private ScriptRepository scriptRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private ScriptQueryServiceImpl scriptQueryService;
    private ScriptServiceImpl scriptService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        scriptQueryService = new ScriptQueryServiceImpl(scriptRepository, blobQueryService,
                Mappers.getMapper(ScriptMapper.class), loggingService);
        scriptService = new ScriptServiceImpl(scriptRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    @Test
    void getScript_scriptOfOrganization_returnsContentAsUtf8() {
        Script script = Script.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .name("check-links.sh")
                .executable(true)
                .build();
        byte[] content = "#!/usr/bin/env bash\necho ok\n".getBytes(StandardCharsets.UTF_8);
        Blob blob = Blob.builder().id(script.getContentBlobId()).content(content).size(content.length).build();
        when(scriptRepository.findByIdAndOrganizationId(script.getId(), organizationId)).thenReturn(Optional.of(script));
        when(blobQueryService.getEntityById(organizationId, script.getContentBlobId())).thenReturn(blob);

        ScriptDetailResponse result = scriptQueryService.getScript(organizationId, script.getId());

        assertEquals("#!/usr/bin/env bash\necho ok\n", result.content());
        assertEquals("check-links.sh", result.name());
        assertTrue(result.executable());
        assertEquals(content.length, result.sizeBytes());
        assertEquals(versionId, result.versionId());
    }

    @Test
    void getScript_unknownOrOtherOrganization_throwsNotFound() {
        UUID scriptId = UUID.randomUUID();
        when(scriptRepository.findByIdAndOrganizationId(scriptId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> scriptQueryService.getScript(organizationId, scriptId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_indexRows_deletesFirstAndWritesInGivenOrder() {
        ScriptIndex executable = new ScriptIndex("run.sh", UUID.randomUUID(), true);
        ScriptIndex plain = new ScriptIndex("helper.py", UUID.randomUUID(), false);
        when(scriptRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Script> result = scriptService.replaceVersion(organizationId, versionId, List.of(executable, plain));

        InOrder order = inOrder(scriptRepository);
        order.verify(scriptRepository).deleteByVersion(organizationId, versionId);
        order.verify(scriptRepository).saveAll(any(List.class));
        assertEquals(2, result.size());
        assertEquals("run.sh", result.get(0).getName());
        assertTrue(result.get(0).getExecutable());
        assertEquals("helper.py", result.get(1).getName());
        assertFalse(result.get(1).getExecutable());
        assertEquals(organizationId, result.get(1).getOrganizationId());
        assertEquals(versionId, result.get(1).getVersionId());
        assertEquals(plain.contentBlobId(), result.get(1).getContentBlobId());
    }

    @Test
    void savePart_index_addsOneRowWithoutDeleting() {
        UUID blobId = UUID.randomUUID();
        when(scriptRepository.save(any(Script.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Script result = scriptService.savePart(organizationId, versionId, new ScriptIndex("run.sh", blobId, true));

        assertEquals(blobId, result.getContentBlobId());
        verify(scriptRepository, never()).deleteByVersion(any(), any());
    }

    @Test
    void deleteParts_noIds_deletesNothing() {
        assertEquals(0, scriptService.deleteParts(organizationId, versionId, List.of()));

        verify(scriptRepository, never()).deleteParts(any(), any(), anyCollection());
    }

    @Test
    void deleteParts_ids_deletesThem() {
        List<UUID> partIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(scriptRepository.deleteParts(organizationId, versionId, partIds)).thenReturn(2);

        assertEquals(2, scriptService.deleteParts(organizationId, versionId, partIds));
    }
}
