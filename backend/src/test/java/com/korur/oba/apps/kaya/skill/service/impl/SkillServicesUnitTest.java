package com.korur.oba.apps.kaya.skill.service.impl;

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
import com.korur.oba.apps.kaya.convention.entity.Convention;
import com.korur.oba.apps.kaya.convention.service.ConventionQueryService;
import com.korur.oba.apps.kaya.reference.service.ReferenceQueryService;
import com.korur.oba.apps.kaya.script.entity.Script;
import com.korur.oba.apps.kaya.script.service.ScriptQueryService;
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillIndex;
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillPartIndex;
import com.korur.oba.apps.kaya.skill.dto.projection.SkillPartUsageProjection;
import com.korur.oba.apps.kaya.skill.dto.request.SkillQueryRequest;
import com.korur.oba.apps.kaya.skill.dto.response.SkillDetailResponse;
import com.korur.oba.apps.kaya.skill.dto.response.SkillListResponse;
import com.korur.oba.apps.kaya.skill.entity.Skill;
import com.korur.oba.apps.kaya.skill.entity.SkillReference;
import com.korur.oba.apps.kaya.skill.entity.SkillScript;
import com.korur.oba.apps.kaya.skill.entity.SkillWorkflow;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.skill.mapper.SkillMapper;
import com.korur.oba.apps.kaya.skill.repository.SkillConventionRepository;
import com.korur.oba.apps.kaya.skill.repository.SkillReferenceRepository;
import com.korur.oba.apps.kaya.skill.repository.SkillRepository;
import com.korur.oba.apps.kaya.skill.repository.SkillScriptRepository;
import com.korur.oba.apps.kaya.skill.repository.SkillToolParameterRepository;
import com.korur.oba.apps.kaya.skill.repository.SkillWorkflowRepository;
import com.korur.oba.apps.kaya.toolparameter.service.ToolParameterQueryService;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;
import com.korur.oba.apps.kaya.workflow.service.WorkflowQueryService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillServicesUnitTest {

    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillWorkflowRepository skillWorkflowRepository;
    @Mock
    private SkillScriptRepository skillScriptRepository;
    @Mock
    private SkillConventionRepository skillConventionRepository;
    @Mock
    private SkillReferenceRepository skillReferenceRepository;
    @Mock
    private SkillToolParameterRepository skillToolParameterRepository;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private WorkflowQueryService workflowQueryService;
    @Mock
    private ScriptQueryService scriptQueryService;
    @Mock
    private ConventionQueryService conventionQueryService;
    @Mock
    private ReferenceQueryService referenceQueryService;
    @Mock
    private ToolParameterQueryService toolParameterQueryService;
    @Mock
    private LoggingService loggingService;

    private SkillQueryServiceImpl skillQueryService;
    private SkillServiceImpl skillService;
    private UUID organizationId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        skillQueryService = new SkillQueryServiceImpl(skillRepository, skillWorkflowRepository, skillScriptRepository,
                skillConventionRepository, skillReferenceRepository, skillToolParameterRepository, blobQueryService,
                Mappers.getMapper(SkillMapper.class), loggingService);
        skillService = new SkillServiceImpl(skillRepository, workflowQueryService, scriptQueryService,
                conventionQueryService, referenceQueryService, toolParameterQueryService, loggingService);
        organizationId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    private Skill skill(String name) {
        return Skill.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .versionId(versionId)
                .contentBlobId(UUID.randomUUID())
                .name(name)
                .description("A skill")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private SkillQueryRequest request() {
        SkillQueryRequest request = new SkillQueryRequest();
        request.setVersionId(List.of(versionId));
        return request;
    }

    private static SkillPartUsageProjection usage(UUID partId, long skills) {
        return new SkillPartUsageProjection() {
            @Override
            public UUID getPartId() {
                return partId;
            }

            @Override
            public Long getSkills() {
                return skills;
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSkills_skillsFound_returnsPathAsNameAndSizePerSkill() {
        Skill first = skill("api-integration");
        Skill second = skill("logging-convention");
        when(skillRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(first, second));
        when(blobQueryService.getSizesByIds(organizationId, Set.of(first.getContentBlobId(), second.getContentBlobId())))
                .thenReturn(Map.of(first.getContentBlobId(), 900, second.getContentBlobId(), 1200));

        List<SkillListResponse> result = skillQueryService.getSkills(organizationId, request());

        assertEquals(2, result.size());
        assertEquals("api-integration", result.get(0).path());
        assertEquals("api-integration", result.get(0).name());
        assertEquals(900, result.get(0).sizeBytes());
        assertEquals(1200, result.get(1).sizeBytes());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSkills_noSkills_returnsEmptyWithoutBlobQuery() {
        when(skillRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<SkillListResponse> result = skillQueryService.getSkills(organizationId, request());

        assertTrue(result.isEmpty());
        verifyNoInteractions(blobQueryService);
    }

    @Test
    void getSkill_skillWithParts_returnsContentAndPartsByPathWithUsage() {
        Skill skill = skill("shadcn");
        UUID workflowId = UUID.randomUUID();
        UUID scriptId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        SkillWorkflow workflowLink = SkillWorkflow.builder().skill(skill).workflowId(workflowId)
                .path("workflows/add.md").build();
        SkillScript scriptLink = SkillScript.builder().skill(skill).scriptId(scriptId).path("scripts/init.sh").build();
        SkillReference referenceLink = SkillReference.builder().skill(skill).referenceId(referenceId)
                .path("references/api.md").build();
        Blob blob = Blob.builder().id(skill.getContentBlobId()).content("# shadcn\n".getBytes(StandardCharsets.UTF_8))
                .size(9).build();
        when(skillRepository.findByIdAndOrganizationId(skill.getId(), organizationId)).thenReturn(Optional.of(skill));
        when(blobQueryService.getEntityById(organizationId, skill.getContentBlobId())).thenReturn(blob);
        when(skillWorkflowRepository.findBySkill_IdOrderByPathAsc(skill.getId())).thenReturn(List.of(workflowLink));
        when(skillWorkflowRepository.countSkillsByWorkflowIds(List.of(workflowId)))
                .thenReturn(List.of(usage(workflowId, 1)));
        when(skillScriptRepository.findBySkill_IdOrderByPathAsc(skill.getId())).thenReturn(List.of(scriptLink));
        when(skillScriptRepository.countSkillsByScriptIds(List.of(scriptId))).thenReturn(List.of(usage(scriptId, 3)));
        when(skillConventionRepository.findBySkill_IdOrderByPathAsc(skill.getId())).thenReturn(List.of());
        when(skillReferenceRepository.findBySkill_IdOrderByPathAsc(skill.getId())).thenReturn(List.of(referenceLink));
        when(skillReferenceRepository.countSkillsByReferenceIds(List.of(referenceId)))
                .thenReturn(List.of(usage(referenceId, 2)));
        when(skillToolParameterRepository.findBySkill_IdOrderByPathAsc(skill.getId())).thenReturn(List.of());

        SkillDetailResponse result = skillQueryService.getSkill(organizationId, skill.getId());

        assertEquals("# shadcn\n", result.content());
        assertEquals(9, result.sizeBytes());
        assertEquals(List.of("references/api.md", "scripts/init.sh", "workflows/add.md"),
                result.parts().stream().map(part -> part.path()).toList());
        assertEquals(List.of(SkillPartKind.REFERENCE, SkillPartKind.SCRIPT, SkillPartKind.WORKFLOW),
                result.parts().stream().map(part -> part.kind()).toList());
        assertEquals(List.of(referenceId, scriptId, workflowId), result.parts().stream().map(part -> part.id()).toList());
        assertEquals(List.of(2, 3, 1), result.parts().stream().map(part -> part.usedBySkills()).toList());
        verify(skillConventionRepository, never()).countSkillsByConventionIds(anyList());
    }

    @Test
    void getSkill_otherOrganization_throwsNotFound() {
        UUID skillId = UUID.randomUUID();
        when(skillRepository.findByIdAndOrganizationId(skillId, organizationId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> skillQueryService.getSkill(organizationId, skillId));
        verifyNoInteractions(blobQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceVersion_partsOfVersion_deletesFirstThenSavesLinksPerKind() {
        Workflow workflow = Workflow.builder().id(UUID.randomUUID()).build();
        Script script = Script.builder().id(UUID.randomUUID()).build();
        when(workflowQueryService.getEntitiesByVersionId(organizationId, versionId)).thenReturn(List.of(workflow));
        when(scriptQueryService.getEntitiesByVersionId(organizationId, versionId)).thenReturn(List.of(script));
        when(skillRepository.deleteByVersion(organizationId, versionId)).thenReturn(1);
        when(skillRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        SkillIndex first = new SkillIndex("first", UUID.randomUUID(), null, List.of(
                new SkillPartIndex(SkillPartKind.WORKFLOW, workflow.getId(), "workflows/add.md"),
                new SkillPartIndex(SkillPartKind.SCRIPT, script.getId(), "scripts/init.sh")));
        // A shared script is linked by the second skill too; its kind is loaded once.
        SkillIndex second = new SkillIndex("second", UUID.randomUUID(), "Second", List.of(
                new SkillPartIndex(SkillPartKind.SCRIPT, script.getId(), "scripts/init.sh")));

        List<Skill> result = skillService.replaceVersion(organizationId, versionId, List.of(first, second));

        InOrder order = inOrder(skillRepository);
        order.verify(skillRepository).deleteByVersion(organizationId, versionId);
        ArgumentCaptor<List<Skill>> saved = ArgumentCaptor.forClass(List.class);
        order.verify(skillRepository).saveAll(saved.capture());
        assertEquals(List.of("first", "second"), result.stream().map(Skill::getName).toList());
        Skill firstSaved = saved.getValue().get(0);
        assertEquals(organizationId, firstSaved.getOrganizationId());
        assertEquals(versionId, firstSaved.getVersionId());
        assertEquals(workflow.getId(), firstSaved.getWorkflows().get(0).getWorkflowId());
        assertEquals(firstSaved, firstSaved.getWorkflows().get(0).getSkill());
        assertEquals("scripts/init.sh", firstSaved.getScripts().get(0).getPath());
        assertEquals(script.getId(), saved.getValue().get(1).getScripts().get(0).getScriptId());
        verify(scriptQueryService).getEntitiesByVersionId(organizationId, versionId);
        verifyNoInteractions(conventionQueryService, referenceQueryService, toolParameterQueryService);
    }

    @Test
    void replaceVersion_partOfAnotherVersion_throwsNotFoundAndKeepsSkills() {
        when(referenceQueryService.getEntitiesByVersionId(organizationId, versionId)).thenReturn(List.of());
        SkillIndex index = new SkillIndex("shadcn", UUID.randomUUID(), null, List.of(
                new SkillPartIndex(SkillPartKind.REFERENCE, UUID.randomUUID(), "references/api.md")));

        assertThrows(NotFoundException.class,
                () -> skillService.replaceVersion(organizationId, versionId, List.of(index)));
        verify(skillRepository, never()).deleteByVersion(any(), any());
        verify(skillRepository, never()).saveAll(anyList());
    }

    @Test
    void replaceVersion_noSkills_deletesVersionSkillsWithoutPartLookups() {
        when(skillRepository.deleteByVersion(organizationId, versionId)).thenReturn(2);
        when(skillRepository.saveAll(anyList())).thenReturn(List.of());

        List<Skill> result = skillService.replaceVersion(organizationId, versionId, List.of());

        assertTrue(result.isEmpty());
        verify(skillRepository).deleteByVersion(organizationId, versionId);
        verifyNoInteractions(workflowQueryService, scriptQueryService, conventionQueryService, referenceQueryService,
                toolParameterQueryService);
    }

    @Test
    void saveSkill_partOfVersion_deletesTheOldSkillFirstAndWritesItWithLinks() {
        Convention convention = Convention.builder().id(UUID.randomUUID()).build();
        when(conventionQueryService.getEntitiesByVersionId(organizationId, versionId)).thenReturn(List.of(convention));
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SkillIndex index = new SkillIndex("shadcn", UUID.randomUUID(), "UI", List.of(
                new SkillPartIndex(SkillPartKind.CONVENTION, convention.getId(), "rules/forms.md")));

        Skill result = skillService.saveSkill(organizationId, versionId, index);

        InOrder order = inOrder(skillRepository);
        order.verify(skillRepository).deleteByName(organizationId, versionId, "shadcn");
        order.verify(skillRepository).save(any(Skill.class));
        assertEquals("shadcn", result.getName());
        assertEquals(convention.getId(), result.getConventions().get(0).getConventionId());
        assertEquals("rules/forms.md", result.getConventions().get(0).getPath());
    }

    @Test
    void saveSkill_partOfAnotherVersion_throwsNotFoundAndKeepsTheSkill() {
        when(toolParameterQueryService.getEntitiesByVersionId(organizationId, versionId)).thenReturn(List.of());
        SkillIndex index = new SkillIndex("shadcn", UUID.randomUUID(), null, List.of(
                new SkillPartIndex(SkillPartKind.TOOL_PARAMETER, UUID.randomUUID(), "evals/evals.json")));

        assertThrows(NotFoundException.class, () -> skillService.saveSkill(organizationId, versionId, index));
        verify(skillRepository, never()).deleteByName(any(), any(), any());
        verify(skillRepository, never()).save(any(Skill.class));
    }

    @Test
    void deleteSkill_skillOfThatName_returnsTrue() {
        when(skillRepository.deleteByName(organizationId, versionId, "shadcn")).thenReturn(1);

        assertTrue(skillService.deleteSkill(organizationId, versionId, "shadcn"));
    }

    @Test
    void deleteSkill_noSkillOfThatName_returnsFalse() {
        when(skillRepository.deleteByName(organizationId, versionId, "shadcn")).thenReturn(0);

        assertFalse(skillService.deleteSkill(organizationId, versionId, "shadcn"));
    }
}
