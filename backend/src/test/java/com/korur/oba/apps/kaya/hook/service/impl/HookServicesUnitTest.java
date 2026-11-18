package com.korur.oba.apps.kaya.hook.service.impl;

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
import com.korur.oba.apps.kaya.hook.dto.embedded.HookIndex;
import com.korur.oba.apps.kaya.hook.dto.request.HookQueryRequest;
import com.korur.oba.apps.kaya.hook.dto.response.HookDetailResponse;
import com.korur.oba.apps.kaya.hook.dto.response.HookListResponse;
import com.korur.oba.apps.kaya.hook.entity.Hook;
import com.korur.oba.apps.kaya.hook.mapper.HookMapper;
import com.korur.oba.apps.kaya.hook.repository.HookRepository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HookServicesUnitTest {

    @Mock
    private HookRepository hookRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private HookQueryServiceImpl hookQueryService;
    private HookServiceImpl hookService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        hookQueryService = new HookQueryServiceImpl(hookRepository, blobQueryService,
                Mappers.getMapper(HookMapper.class), loggingService);
        hookService = new HookServiceImpl(hookRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    private Hook hook(String relativePath) {
        return Hook.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .relativePath(relativePath)
                .executable(true)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getHooks_hooksFound_returnsIndexFieldsAndSizes() {
        Hook first = hook("a.sh");
        Hook second = hook("b.sh");
        HookQueryRequest request = new HookQueryRequest();
        request.setVersionId(List.of(versionId));
        when(hookRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(first, second));
        when(blobQueryService.getSizesByIds(organizationId, Set.of(first.getContentBlobId(), second.getContentBlobId())))
                .thenReturn(Map.of(first.getContentBlobId(), 900, second.getContentBlobId(), 50));

        List<HookListResponse> result = hookQueryService.getHooks(organizationId, request);

        assertEquals(2, result.size());
        assertEquals("a.sh", result.get(0).path());
        assertEquals(900, result.get(0).sizeBytes());
        assertEquals(50, result.get(1).sizeBytes());
        assertEquals("b.sh", result.get(1).path());
        assertEquals(Boolean.TRUE, result.get(0).executable());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getHooks_noHooks_skipsBlobQuery() {
        HookQueryRequest request = new HookQueryRequest();
        request.setVersionId(List.of(versionId));
        when(hookRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<HookListResponse> result = hookQueryService.getHooks(organizationId, request);

        assertEquals(0, result.size());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    void getHook_hookOfOrganization_returnsContentAsUtf8() {
        Hook hook = hook("a.sh");
        byte[] content = "#!/usr/bin/env bash\necho ok\n".getBytes(StandardCharsets.UTF_8);
        Blob blob = Blob.builder().id(hook.getContentBlobId()).content(content).size(content.length).build();
        when(hookRepository.findByIdAndOrganizationId(hook.getId(), organizationId)).thenReturn(Optional.of(hook));
        when(blobQueryService.getEntityById(organizationId, hook.getContentBlobId())).thenReturn(blob);

        HookDetailResponse result = hookQueryService.getHook(organizationId, hook.getId());

        assertEquals("#!/usr/bin/env bash\necho ok\n", result.content());
        assertEquals("a.sh", result.path());
        assertEquals(content.length, result.sizeBytes());
    }

    @Test
    void getHook_unknownOrOtherOrganization_throwsNotFound() {
        UUID hookId = UUID.randomUUID();
        when(hookRepository.findByIdAndOrganizationId(hookId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> hookQueryService.getHook(organizationId, hookId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_indexRows_deletesFirstAndWritesHooks() {
        UUID blobId = UUID.randomUUID();
        HookIndex index = new HookIndex("dispatch.sh", blobId, null);
        when(hookRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Hook> result = hookService.replaceVersion(organizationId, versionId, List.of(index));

        InOrder order = inOrder(hookRepository);
        order.verify(hookRepository).deleteByVersion(organizationId, versionId);
        order.verify(hookRepository).saveAll(any(List.class));
        assertEquals(1, result.size());
        assertEquals(organizationId, result.get(0).getOrganizationId());
        assertEquals(versionId, result.get(0).getVersionId());
        assertEquals(blobId, result.get(0).getContentBlobId());
        assertEquals(Boolean.FALSE, result.get(0).getExecutable());
    }

    @Test
    void saveFile_index_deletesTheOldRowFirstAndWritesOne() {
        UUID blobId = UUID.randomUUID();
        when(hookRepository.save(any(Hook.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Hook result = hookService.saveFile(organizationId, versionId, new HookIndex("dispatch.sh", blobId, true));

        InOrder order = inOrder(hookRepository);
        order.verify(hookRepository).deleteByPath(organizationId, versionId, "dispatch.sh");
        order.verify(hookRepository).save(any(Hook.class));
        assertEquals(organizationId, result.getOrganizationId());
        assertEquals(versionId, result.getVersionId());
        assertEquals(blobId, result.getContentBlobId());
    }

    @Test
    void deleteFile_noRowAtPath_returnsFalse() {
        when(hookRepository.deleteByPath(organizationId, versionId, "dispatch.sh")).thenReturn(0);

        assertFalse(hookService.deleteFile(organizationId, versionId, "dispatch.sh"));
    }

    @Test
    void deleteFile_rowAtPath_returnsTrue() {
        when(hookRepository.deleteByPath(organizationId, versionId, "dispatch.sh")).thenReturn(1);

        assertTrue(hookService.deleteFile(organizationId, versionId, "dispatch.sh"));
    }
}
