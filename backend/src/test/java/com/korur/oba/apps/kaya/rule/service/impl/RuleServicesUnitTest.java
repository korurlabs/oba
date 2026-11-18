package com.korur.oba.apps.kaya.rule.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import org.mapstruct.factory.Mappers;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
import com.korur.oba.apps.kaya.rule.dto.embedded.RuleIndex;
import com.korur.oba.apps.kaya.rule.dto.request.RuleQueryRequest;
import com.korur.oba.apps.kaya.rule.dto.response.RuleDetailResponse;
import com.korur.oba.apps.kaya.rule.dto.response.RuleListResponse;
import com.korur.oba.apps.kaya.rule.entity.Rule;
import com.korur.oba.apps.kaya.rule.entity.RuleConvention;
import com.korur.oba.apps.kaya.rule.mapper.RuleMapper;
import com.korur.oba.apps.kaya.rule.repository.RuleConventionRepository;
import com.korur.oba.apps.kaya.rule.repository.RuleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleServicesUnitTest {

    @Mock
    private RuleRepository ruleRepository;
    @Mock
    private RuleConventionRepository ruleConventionRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private LoggingService loggingService;

    private RuleQueryServiceImpl ruleQueryService;
    private RuleServiceImpl ruleService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        ruleQueryService = new RuleQueryServiceImpl(ruleRepository, ruleConventionRepository, blobQueryService,
                Mappers.getMapper(RuleMapper.class), loggingService);
        ruleService = new RuleServiceImpl(ruleRepository, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    private Rule rule(String relativePath, List<String> paths) {
        return Rule.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .relativePath(relativePath)
                .paths(new ArrayList<>(paths))
                .alwaysOn(paths.isEmpty())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private RuleQueryRequest request() {
        RuleQueryRequest request = new RuleQueryRequest();
        request.setVersionId(List.of(versionId));
        return request;
    }

    @Test
    @SuppressWarnings("unchecked")
    void getRules_rulesFound_returnsConventionsAndSizesPerRule() {
        Rule first = rule("00-core.md", List.of());
        Rule second = rule("11-backend.md", List.of("projects/kaya/backend/**"));
        RuleConvention link = RuleConvention.builder().rule(first)
                .conventionPath("docs/private/conventions/backend/logging.md").build();
        when(ruleRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(first, second));
        when(ruleConventionRepository.findByRule_IdInOrderByConventionPathAsc(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(link));
        when(blobQueryService.getSizesByIds(organizationId, Set.of(first.getContentBlobId(), second.getContentBlobId())))
                .thenReturn(Map.of(first.getContentBlobId(), 120, second.getContentBlobId(), 340));

        List<RuleListResponse> result = ruleQueryService.getRules(organizationId, request());

        assertEquals(2, result.size());
        assertEquals("00-core.md", result.get(0).path());
        assertTrue(result.get(0).alwaysOn());
        assertEquals(120, result.get(0).sizeBytes());
        assertEquals(List.of("docs/private/conventions/backend/logging.md"),
                result.get(0).conventions().stream().map(c -> c.conventionPath()).toList());
        assertEquals(340, result.get(1).sizeBytes());
        assertTrue(result.get(1).conventions().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getRules_noRules_returnsEmptyWithoutBlobQuery() {
        when(ruleRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<RuleListResponse> result = ruleQueryService.getRules(organizationId, request());

        assertTrue(result.isEmpty());
        verifyNoInteractions(blobQueryService);
        verify(ruleConventionRepository, never()).findByRule_IdInOrderByConventionPathAsc(anyCollection());
    }

    @Test
    void getRule_ruleOfOrganization_returnsContentAsUtf8() {
        Rule rule = rule("00-core.md", List.of());
        byte[] content = "---\ndescription: Kernüberblick\n---\n# Core\n".getBytes(StandardCharsets.UTF_8);
        Blob blob = Blob.builder().id(rule.getContentBlobId()).content(content).size(content.length).build();
        when(ruleRepository.findByIdAndOrganizationId(rule.getId(), organizationId)).thenReturn(Optional.of(rule));
        when(blobQueryService.getEntityById(organizationId, rule.getContentBlobId())).thenReturn(blob);
        when(ruleConventionRepository.findByRule_IdInOrderByConventionPathAsc(List.of(rule.getId())))
                .thenReturn(List.of());

        RuleDetailResponse result = ruleQueryService.getRule(organizationId, rule.getId());

        assertEquals(new String(content, StandardCharsets.UTF_8), result.content());
        assertEquals(content.length, result.sizeBytes());
        assertEquals(rule.getCreatedAt(), result.createdAt());
    }

    @Test
    void getRule_unknownOrOtherOrganization_throwsNotFound() {
        UUID ruleId = UUID.randomUUID();
        when(ruleRepository.findByIdAndOrganizationId(ruleId, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> ruleQueryService.getRule(organizationId, ruleId));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_indexRows_deletesFirstAndWritesRulesWithLinks() {
        UUID blobId = UUID.randomUUID();
        RuleIndex alwaysOn = new RuleIndex("00-core.md", blobId, "Core", List.of(), null,
                List.of("docs/private/conventions/a.md", "docs/private/conventions/a.md"));
        RuleIndex scoped = new RuleIndex("11-backend.md", UUID.randomUUID(), null,
                List.of("projects/kaya/backend/**"), "11-backend.md", null);
        when(ruleRepository.deleteByVersion(organizationId, versionId)).thenReturn(3);
        when(ruleRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Rule> result = ruleService.replaceVersion(organizationId, versionId, List.of(alwaysOn, scoped));

        InOrder order = inOrder(ruleRepository);
        order.verify(ruleRepository).deleteByVersion(organizationId, versionId);
        ArgumentCaptor<List<Rule>> saved = ArgumentCaptor.forClass(List.class);
        order.verify(ruleRepository).saveAll(saved.capture());
        assertSame(saved.getValue(), result);

        Rule core = result.get(0);
        assertEquals(organizationId, core.getOrganizationId());
        assertEquals(versionId, core.getVersionId());
        assertEquals(blobId, core.getContentBlobId());
        assertTrue(core.getAlwaysOn());
        assertEquals(1, core.getConventions().size(), "a path cited twice is linked once");
        assertSame(core, core.getConventions().get(0).getRule());

        Rule backend = result.get(1);
        assertFalse(backend.getAlwaysOn());
        assertEquals("11-backend.md", backend.getOverridesPath());
        assertNull(backend.getDescription());
        assertTrue(backend.getConventions().isEmpty());
    }

    @Test
    void saveFile_index_deletesTheOldRowFirstAndWritesOne() {
        UUID blobId = UUID.randomUUID();
        when(ruleRepository.save(any(Rule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Rule result = ruleService.saveFile(organizationId, versionId, new RuleIndex("00-core.md", blobId, "Core", List.of(), null, List.of("docs/private/conventions/a.md")));

        InOrder order = inOrder(ruleRepository);
        order.verify(ruleRepository).deleteByPath(organizationId, versionId, "00-core.md");
        order.verify(ruleRepository).save(any(Rule.class));
        assertEquals(organizationId, result.getOrganizationId());
        assertEquals(versionId, result.getVersionId());
        assertEquals(blobId, result.getContentBlobId());
    }

    @Test
    void deleteFile_noRowAtPath_returnsFalse() {
        when(ruleRepository.deleteByPath(organizationId, versionId, "00-core.md")).thenReturn(0);

        assertFalse(ruleService.deleteFile(organizationId, versionId, "00-core.md"));
    }

    @Test
    void deleteFile_rowAtPath_returnsTrue() {
        when(ruleRepository.deleteByPath(organizationId, versionId, "00-core.md")).thenReturn(1);

        assertTrue(ruleService.deleteFile(organizationId, versionId, "00-core.md"));
    }
}
