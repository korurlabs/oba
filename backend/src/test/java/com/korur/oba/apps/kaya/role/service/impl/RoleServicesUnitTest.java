package com.korur.oba.apps.kaya.role.service.impl;

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
import com.korur.oba.apps.kaya.role.dto.embedded.RoleIndex;
import com.korur.oba.apps.kaya.role.dto.request.RoleQueryRequest;
import com.korur.oba.apps.kaya.role.dto.response.RoleDetailResponse;
import com.korur.oba.apps.kaya.role.dto.response.RoleListResponse;
import com.korur.oba.apps.kaya.role.entity.Role;
import com.korur.oba.apps.kaya.role.mapper.RoleMapper;
import com.korur.oba.apps.kaya.role.repository.RoleRepository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServicesUnitTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private RoleQueryServiceImpl roleQueryService;
    private RoleServiceImpl roleService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        roleQueryService = new RoleQueryServiceImpl(roleRepository, blobQueryService,
                Mappers.getMapper(RoleMapper.class), loggingService);
        roleService = new RoleServiceImpl(roleRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    private Role role(String relativePath) {
        return Role.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .relativePath(relativePath)
                .title("Coding engineer")
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getRoles_rolesFound_returnsPathsAndSizes() {
        Role first = role("coding-engineer.md");
        Role second = role("reviewer.md");
        RoleQueryRequest request = new RoleQueryRequest();
        request.setVersionId(List.of(versionId));
        when(roleRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(first, second));
        when(blobQueryService.getSizesByIds(organizationId, Set.of(first.getContentBlobId(), second.getContentBlobId())))
                .thenReturn(Map.of(first.getContentBlobId(), 900, second.getContentBlobId(), 50));

        List<RoleListResponse> result = roleQueryService.getRoles(organizationId, request);

        assertEquals(2, result.size());
        assertEquals("coding-engineer.md", result.get(0).path());
        assertEquals("Coding engineer", result.get(0).title());
        assertEquals(900, result.get(0).sizeBytes());
        assertEquals("reviewer.md", result.get(1).path());
        assertEquals(50, result.get(1).sizeBytes());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getRoles_noRoles_readsNoSizes() {
        RoleQueryRequest request = new RoleQueryRequest();
        request.setVersionId(List.of(versionId));
        when(roleRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<RoleListResponse> result = roleQueryService.getRoles(organizationId, request);

        assertEquals(0, result.size());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    void getRole_roleOfOrganization_returnsContentAsUtf8() {
        Role role = role("coding-engineer.md");
        byte[] content = "---\ntitle: Coding engineer\n---\n# Coding engineer\n".getBytes(StandardCharsets.UTF_8);
        Blob blob = Blob.builder().id(role.getContentBlobId()).content(content).size(content.length).build();
        when(roleRepository.findByIdAndOrganizationId(role.getId(), organizationId)).thenReturn(Optional.of(role));
        when(blobQueryService.getEntityById(organizationId, role.getContentBlobId())).thenReturn(blob);

        RoleDetailResponse result = roleQueryService.getRole(organizationId, role.getId());

        assertEquals("---\ntitle: Coding engineer\n---\n# Coding engineer\n", result.content());
        assertEquals("coding-engineer.md", result.path());
        assertEquals(content.length, result.sizeBytes());
    }

    @Test
    void getRole_unknownOrOtherOrganization_throwsNotFound() {
        UUID roleId = UUID.randomUUID();
        when(roleRepository.findByIdAndOrganizationId(roleId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> roleQueryService.getRole(organizationId, roleId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_indexRows_deletesFirstAndWritesRoles() {
        UUID blobId = UUID.randomUUID();
        RoleIndex index = new RoleIndex("coding-engineer.md", blobId, "Coding engineer");
        when(roleRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Role> result = roleService.replaceVersion(organizationId, versionId, List.of(index));

        InOrder order = inOrder(roleRepository);
        order.verify(roleRepository).deleteByVersion(organizationId, versionId);
        order.verify(roleRepository).saveAll(any(List.class));
        assertEquals(1, result.size());
        assertEquals(organizationId, result.get(0).getOrganizationId());
        assertEquals(versionId, result.get(0).getVersionId());
        assertEquals(blobId, result.get(0).getContentBlobId());
        assertEquals("coding-engineer.md", result.get(0).getRelativePath());
        assertEquals("Coding engineer", result.get(0).getTitle());
    }

    @Test
    void saveFile_index_deletesTheOldRowFirstAndWritesOne() {
        UUID blobId = UUID.randomUUID();
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role result = roleService.saveFile(organizationId, versionId, new RoleIndex("reviewer.md", blobId, "Reviewer"));

        InOrder order = inOrder(roleRepository);
        order.verify(roleRepository).deleteByPath(organizationId, versionId, "reviewer.md");
        order.verify(roleRepository).save(any(Role.class));
        assertEquals(organizationId, result.getOrganizationId());
        assertEquals(versionId, result.getVersionId());
        assertEquals(blobId, result.getContentBlobId());
    }

    @Test
    void deleteFile_noRowAtPath_returnsFalse() {
        when(roleRepository.deleteByPath(organizationId, versionId, "reviewer.md")).thenReturn(0);

        assertFalse(roleService.deleteFile(organizationId, versionId, "reviewer.md"));
    }

    @Test
    void deleteFile_rowAtPath_returnsTrue() {
        when(roleRepository.deleteByPath(organizationId, versionId, "reviewer.md")).thenReturn(1);

        assertTrue(roleService.deleteFile(organizationId, versionId, "reviewer.md"));
    }
}
