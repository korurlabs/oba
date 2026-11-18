package com.korur.oba.apps.kaya.render.mock_data;

// 1. Framework-level
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

// 4. Internal
import com.fasterxml.jackson.databind.ObjectMapper;
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.agentsfile.dto.embedded.AgentsFileIndex;
import com.korur.oba.apps.kaya.agentsfile.entity.AgentsFile;
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.agentsfile.service.AgentsFileQueryService;
import com.korur.oba.apps.kaya.agentsfile.service.AgentsFileService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.blob.service.BlobService;
import com.korur.oba.apps.kaya.convention.dto.embedded.ConventionIndex;
import com.korur.oba.apps.kaya.convention.entity.Convention;
import com.korur.oba.apps.kaya.convention.service.ConventionQueryService;
import com.korur.oba.apps.kaya.convention.service.ConventionService;
import com.korur.oba.apps.kaya.guardrail.dto.embedded.GuardrailIndex;
import com.korur.oba.apps.kaya.guardrail.entity.Guardrail;
import com.korur.oba.apps.kaya.guardrail.service.GuardrailQueryService;
import com.korur.oba.apps.kaya.guardrail.service.GuardrailService;
import com.korur.oba.apps.kaya.hook.dto.embedded.HookIndex;
import com.korur.oba.apps.kaya.hook.entity.Hook;
import com.korur.oba.apps.kaya.hook.service.HookQueryService;
import com.korur.oba.apps.kaya.hook.service.HookService;
import com.korur.oba.apps.kaya.reference.dto.embedded.ReferenceIndex;
import com.korur.oba.apps.kaya.reference.entity.Reference;
import com.korur.oba.apps.kaya.reference.service.ReferenceQueryService;
import com.korur.oba.apps.kaya.reference.service.ReferenceService;
import com.korur.oba.apps.kaya.render.service.impl.RenderQueryServiceImpl;
import com.korur.oba.apps.kaya.render.service.impl.RenderServiceImpl;
import com.korur.oba.apps.kaya.role.dto.embedded.RoleIndex;
import com.korur.oba.apps.kaya.role.entity.Role;
import com.korur.oba.apps.kaya.role.service.RoleQueryService;
import com.korur.oba.apps.kaya.role.service.RoleService;
import com.korur.oba.apps.kaya.rule.dto.embedded.RuleIndex;
import com.korur.oba.apps.kaya.rule.entity.Rule;
import com.korur.oba.apps.kaya.rule.entity.RuleConvention;
import com.korur.oba.apps.kaya.rule.service.RuleQueryService;
import com.korur.oba.apps.kaya.rule.service.RuleService;
import com.korur.oba.apps.kaya.script.dto.embedded.ScriptIndex;
import com.korur.oba.apps.kaya.script.entity.Script;
import com.korur.oba.apps.kaya.script.service.ScriptQueryService;
import com.korur.oba.apps.kaya.script.service.ScriptService;
import com.korur.oba.apps.kaya.settings.dto.embedded.SettingsIndex;
import com.korur.oba.apps.kaya.settings.entity.Settings;
import com.korur.oba.apps.kaya.settings.helper.SettingsParser;
import com.korur.oba.apps.kaya.settings.service.SettingsQueryService;
import com.korur.oba.apps.kaya.settings.service.SettingsService;
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillIndex;
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillPartIndex;
import com.korur.oba.apps.kaya.skill.entity.Skill;
import com.korur.oba.apps.kaya.skill.entity.SkillConvention;
import com.korur.oba.apps.kaya.skill.entity.SkillReference;
import com.korur.oba.apps.kaya.skill.entity.SkillScript;
import com.korur.oba.apps.kaya.skill.entity.SkillToolParameter;
import com.korur.oba.apps.kaya.skill.entity.SkillWorkflow;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.skill.service.SkillQueryService;
import com.korur.oba.apps.kaya.skill.service.SkillService;
import com.korur.oba.apps.kaya.subagent.dto.embedded.SubagentIndex;
import com.korur.oba.apps.kaya.subagent.entity.Subagent;
import com.korur.oba.apps.kaya.subagent.service.SubagentQueryService;
import com.korur.oba.apps.kaya.subagent.service.SubagentService;
import com.korur.oba.apps.kaya.template.dto.embedded.TemplateIndex;
import com.korur.oba.apps.kaya.template.entity.Template;
import com.korur.oba.apps.kaya.template.service.TemplateQueryService;
import com.korur.oba.apps.kaya.template.service.TemplateService;
import com.korur.oba.apps.kaya.toollink.entity.ToolLink;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;
import com.korur.oba.apps.kaya.toollink.service.ToolLinkQueryService;
import com.korur.oba.apps.kaya.toollink.service.ToolLinkService;
import com.korur.oba.apps.kaya.toolparameter.dto.embedded.ToolParameterIndex;
import com.korur.oba.apps.kaya.toolparameter.entity.ToolParameter;
import com.korur.oba.apps.kaya.toolparameter.service.ToolParameterQueryService;
import com.korur.oba.apps.kaya.toolparameter.service.ToolParameterService;
import com.korur.oba.apps.kaya.validation.helper.CompositionRules;
import com.korur.oba.apps.kaya.validation.helper.FileRules;
import com.korur.oba.apps.kaya.validation.service.impl.ValidationQueryServiceImpl;
import com.korur.oba.apps.kaya.workflow.dto.embedded.WorkflowIndex;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;
import com.korur.oba.apps.kaya.workflow.service.WorkflowQueryService;
import com.korur.oba.apps.kaya.workflow.service.WorkflowService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * An in-memory store of one version's kind rows and blobs behind mocked kind and blob services, so
 * the real {@link RenderServiceImpl} and {@link RenderQueryServiceImpl} run against it. It keeps the
 * rows as the database would, including the removal of skill links to a deleted part.
 */
public final class RenderStore {

    public final UUID organizationId = UUID.randomUUID();
    public final UUID versionId = UUID.randomUUID();

    public final Map<UUID, Blob> blobs = new HashMap<>();
    public final List<AgentsFile> agentsFiles = new ArrayList<>();
    public final List<Rule> rules = new ArrayList<>();
    public final List<Guardrail> guardrails = new ArrayList<>();
    public final List<Convention> conventions = new ArrayList<>();
    public final List<Role> roles = new ArrayList<>();
    public final List<Workflow> workflows = new ArrayList<>();
    public final List<Template> templates = new ArrayList<>();
    public final List<Hook> hooks = new ArrayList<>();
    public final List<Subagent> subagents = new ArrayList<>();
    public final List<Settings> settings = new ArrayList<>();
    public final List<ToolLink> toolLinks = new ArrayList<>();
    public final List<Script> scripts = new ArrayList<>();
    public final List<Reference> references = new ArrayList<>();
    public final List<ToolParameter> toolParameters = new ArrayList<>();
    public final List<Skill> skills = new ArrayList<>();

    public final BlobService blobService = Mockito.mock(BlobService.class);
    public final AgentsFileService agentsFileService = Mockito.mock(AgentsFileService.class);
    public final RuleService ruleService = Mockito.mock(RuleService.class);
    public final GuardrailService guardrailService = Mockito.mock(GuardrailService.class);
    public final ConventionService conventionService = Mockito.mock(ConventionService.class);
    public final RoleService roleService = Mockito.mock(RoleService.class);
    public final WorkflowService workflowService = Mockito.mock(WorkflowService.class);
    public final TemplateService templateService = Mockito.mock(TemplateService.class);
    public final HookService hookService = Mockito.mock(HookService.class);
    public final SubagentService subagentService = Mockito.mock(SubagentService.class);
    public final SettingsService settingsService = Mockito.mock(SettingsService.class);
    public final ToolLinkService toolLinkService = Mockito.mock(ToolLinkService.class);
    public final ScriptService scriptService = Mockito.mock(ScriptService.class);
    public final ReferenceService referenceService = Mockito.mock(ReferenceService.class);
    public final ToolParameterService toolParameterService = Mockito.mock(ToolParameterService.class);
    public final SkillService skillService = Mockito.mock(SkillService.class);

    private final BlobQueryService blobQueryService = Mockito.mock(BlobQueryService.class);
    private final AgentsFileQueryService agentsFileQueryService = Mockito.mock(AgentsFileQueryService.class);
    private final RuleQueryService ruleQueryService = Mockito.mock(RuleQueryService.class);
    private final GuardrailQueryService guardrailQueryService = Mockito.mock(GuardrailQueryService.class);
    private final ConventionQueryService conventionQueryService = Mockito.mock(ConventionQueryService.class);
    private final RoleQueryService roleQueryService = Mockito.mock(RoleQueryService.class);
    private final WorkflowQueryService workflowQueryService = Mockito.mock(WorkflowQueryService.class);
    private final TemplateQueryService templateQueryService = Mockito.mock(TemplateQueryService.class);
    private final HookQueryService hookQueryService = Mockito.mock(HookQueryService.class);
    private final SubagentQueryService subagentQueryService = Mockito.mock(SubagentQueryService.class);
    private final SettingsQueryService settingsQueryService = Mockito.mock(SettingsQueryService.class);
    private final ToolLinkQueryService toolLinkQueryService = Mockito.mock(ToolLinkQueryService.class);
    private final ScriptQueryService scriptQueryService = Mockito.mock(ScriptQueryService.class);
    private final ReferenceQueryService referenceQueryService = Mockito.mock(ReferenceQueryService.class);
    private final ToolParameterQueryService toolParameterQueryService = Mockito.mock(ToolParameterQueryService.class);
    private final SkillQueryService skillQueryService = Mockito.mock(SkillQueryService.class);

    public final RenderQueryServiceImpl renderQueryService;
    public final RenderServiceImpl renderService;

    public RenderStore() {
        LoggingService loggingService = Mockito.mock(LoggingService.class);
        stubBlobs();
        stubReads();
        stubWrites();
        SettingsParser settingsParser = new SettingsParser(new ObjectMapper());
        renderQueryService = new RenderQueryServiceImpl(agentsFileQueryService, ruleQueryService, guardrailQueryService, conventionQueryService,
                roleQueryService, workflowQueryService, templateQueryService, hookQueryService, subagentQueryService,
                settingsQueryService, toolLinkQueryService, skillQueryService, scriptQueryService, referenceQueryService,
                toolParameterQueryService, blobQueryService, settingsParser, loggingService);
        ValidationQueryServiceImpl validationQueryService = new ValidationQueryServiceImpl(new FileRules(), new CompositionRules(),
                loggingService);
        renderService = new RenderServiceImpl(renderQueryService, validationQueryService, blobService,
                settingsParser, agentsFileService, ruleService, guardrailService, conventionService, conventionQueryService,
                roleService, workflowService, workflowQueryService, templateService, hookService, subagentService,
                subagentQueryService, settingsService, toolLinkService, scriptService, scriptQueryService, referenceService,
                referenceQueryService, toolParameterService, toolParameterQueryService, skillService, skillQueryService,
                loggingService);
    }

    /** Every row of the version, as a count per kind, for checks that nothing changed. */
    public List<Integer> rowCounts() {
        return List.of(agentsFiles.size(), rules.size(), conventions.size(), roles.size(), workflows.size(), templates.size(),
                hooks.size(), subagents.size(), settings.size(), scripts.size(), references.size(),
                toolParameters.size(), skills.size(), toolLinks.size(), guardrails.size());
    }

    private void stubBlobs() {
        lenient().when(blobService.storeContent(any(), any())).thenAnswer(invocation -> {
            byte[] content = invocation.getArgument(1);
            String sha256 = sha256(content);
            return blobs.values().stream().filter(blob -> blob.getSha256().equals(sha256)).findFirst().orElseGet(() -> {
                Blob blob = Blob.builder().id(UUID.randomUUID()).organizationId(organizationId).sha256(sha256)
                        .content(content.clone()).size(content.length).build();
                blobs.put(blob.getId(), blob);
                return blob;
            });
        });
        lenient().when(blobQueryService.getEntitiesByIds(any(), anyCollection())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(1);
            Map<UUID, Blob> found = new HashMap<>();
            ids.stream().filter(blobs::containsKey).forEach(id -> found.put(id, blobs.get(id)));
            return found;
        });
    }

    private void stubReads() {
        lenient().when(agentsFileQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, agentsFiles));
        lenient().when(ruleQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, rules));
        lenient().when(guardrailQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, guardrails));
        lenient().when(conventionQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, conventions));
        lenient().when(roleQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, roles));
        lenient().when(workflowQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, workflows));
        lenient().when(templateQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, templates));
        lenient().when(hookQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, hooks));
        lenient().when(subagentQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, subagents));
        lenient().when(settingsQueryService.getEntityByVersionId(any(), any()))
                .thenAnswer(invocation -> ofOrganization(invocation, settings).stream().findFirst());
        lenient().when(toolLinkQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, toolLinks));
        lenient().when(toolLinkQueryService.getEntitiesByVersionIds(any(), anyCollection())).thenAnswer(invocation -> {
            Collection<UUID> versionIds = invocation.getArgument(1);
            return ofOrganization(invocation, toolLinks).stream().filter(link -> versionIds.contains(link.getVersionId())).toList();
        });
        lenient().when(scriptQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, scripts));
        lenient().when(referenceQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, references));
        lenient().when(toolParameterQueryService.getEntitiesByVersionId(any(), any()))
                .thenAnswer(invocation -> ofOrganization(invocation, toolParameters));
        lenient().when(skillQueryService.getEntitiesByVersionId(any(), any())).thenAnswer(invocation -> ofOrganization(invocation, skills));
        lenient().when(skillQueryService.countByVersionIds(any(), anyCollection()))
                .thenAnswer(invocation -> skillsOfVersions(invocation).size());
        lenient().when(skillQueryService.countPartLinksByVersionIds(any(), anyCollection())).thenAnswer(invocation -> {
            Map<SkillPartKind, Integer> counts = new EnumMap<>(SkillPartKind.class);
            for (SkillPartKind kind : SkillPartKind.values()) {
                counts.put(kind, 0);
            }
            for (Skill skill : skillsOfVersions(invocation)) {
                counts.merge(SkillPartKind.WORKFLOW, skill.getWorkflows().size(), Integer::sum);
                counts.merge(SkillPartKind.SCRIPT, skill.getScripts().size(), Integer::sum);
                counts.merge(SkillPartKind.CONVENTION, skill.getConventions().size(), Integer::sum);
                counts.merge(SkillPartKind.REFERENCE, skill.getReferences().size(), Integer::sum);
                counts.merge(SkillPartKind.TOOL_PARAMETER, skill.getToolParameters().size(), Integer::sum);
            }
            return counts;
        });
    }

    // The skills of the organization whose version is among the ids of the call.
    private List<Skill> skillsOfVersions(InvocationOnMock invocation) {
        Collection<UUID> versionIds = invocation.getArgument(1);
        return ofOrganization(invocation, skills).stream().filter(skill -> versionIds.contains(skill.getVersionId())).toList();
    }

    // The store holds one organization's rows, so a read for another organization finds nothing.
    private <E> List<E> ofOrganization(InvocationOnMock invocation, List<E> rows) {
        return organizationId.equals(invocation.getArgument(0)) ? List.copyOf(rows) : List.of();
    }

    private void stubWrites() {
        lenient().when(agentsFileService.replaceVersion(any(), any(), anyList())).thenAnswer(invocation ->
                replace(agentsFiles, invocation.<List<AgentsFileIndex>>getArgument(2), this::agentsFile));
        lenient().when(agentsFileService.saveFile(any(), any(), any())).thenAnswer(invocation -> {
            AgentsFileIndex index = invocation.getArgument(2);
            agentsFiles.removeIf(row -> row.getPlacement() == index.placement());
            return add(agentsFiles, agentsFile(index));
        });
        lenient().when(agentsFileService.deleteFile(any(), any(), any())).thenAnswer(invocation -> {
            AgentsFilePlacement placement = invocation.getArgument(2);
            return agentsFiles.removeIf(row -> row.getPlacement() == placement);
        });

        stubFiles(() -> ruleService.replaceVersion(any(), any(), anyList()), () -> ruleService.saveFile(any(), any(), any()), () -> ruleService.deleteFile(any(), any(), anyString()), rules, this::rule, Rule::getRelativePath);
        stubFiles(() -> guardrailService.replaceVersion(any(), any(), anyList()), () -> guardrailService.saveFile(any(), any(), any()), () -> guardrailService.deleteFile(any(), any(), anyString()), guardrails, this::guardrail, Guardrail::getName);
        stubFiles(() -> conventionService.replaceVersion(any(), any(), anyList()), () -> conventionService.saveFile(any(), any(), any()), () -> conventionService.deleteFile(any(), any(), anyString()), conventions, this::convention, Convention::getRelativePath);
        stubFiles(() -> roleService.replaceVersion(any(), any(), anyList()), () -> roleService.saveFile(any(), any(), any()), () -> roleService.deleteFile(any(), any(), anyString()), roles, this::role, Role::getRelativePath);
        stubFiles(() -> workflowService.replaceVersion(any(), any(), anyList()), () -> workflowService.saveFile(any(), any(), any()), () -> workflowService.deleteFile(any(), any(), anyString()), workflows, this::workflow, Workflow::getRelativePath);
        stubFiles(() -> templateService.replaceVersion(any(), any(), anyList()), () -> templateService.saveFile(any(), any(), any()), () -> templateService.deleteFile(any(), any(), anyString()), templates, this::template, Template::getRelativePath);
        stubFiles(() -> hookService.replaceVersion(any(), any(), anyList()), () -> hookService.saveFile(any(), any(), any()), () -> hookService.deleteFile(any(), any(), anyString()), hooks, this::hook, Hook::getRelativePath);
        stubFiles(() -> subagentService.replaceVersion(any(), any(), anyList()), () -> subagentService.saveFile(any(), any(), any()), () -> subagentService.deleteFile(any(), any(), anyString()), subagents, this::subagent, Subagent::getRelativePath);

        lenient().when(settingsService.replaceVersion(any(), any(), any())).thenAnswer(invocation -> {
            SettingsIndex index = invocation.getArgument(2);
            settings.clear();
            if (index == null) {
                return Optional.empty();
            }
            return Optional.of(add(settings, Settings.builder().id(UUID.randomUUID()).organizationId(organizationId)
                    .versionId(versionId).contentBlobId(index.contentBlobId()).build()));
        });
        lenient().when(toolLinkService.replaceVersion(any(), any(), anyCollection())).thenAnswer(invocation -> {
            Collection<ToolLinkName> names = invocation.getArgument(2);
            toolLinks.clear();
            names.forEach(name -> toolLinks.add(toolLink(name)));
            return List.copyOf(toolLinks);
        });
        lenient().when(toolLinkService.saveLink(any(), any(), any())).thenAnswer(invocation -> {
            ToolLinkName name = invocation.getArgument(2);
            return toolLinks.stream().filter(row -> row.getName() == name).findFirst().orElseGet(() -> add(toolLinks, toolLink(name)));
        });
        lenient().when(toolLinkService.deleteLink(any(), any(), any())).thenAnswer(invocation -> {
            ToolLinkName name = invocation.getArgument(2);
            return toolLinks.removeIf(row -> row.getName() == name) ? 1 : 0;
        });

        stubParts(() -> workflowService.replaceVersion(any(), any(), anyList()), () -> workflowService.savePart(any(), any(), any()), () -> workflowService.deleteParts(any(), any(), anyCollection()), workflows, this::workflow,
                row -> row.getRelativePath() == null);
        stubParts(() -> conventionService.replaceVersion(any(), any(), anyList()), () -> conventionService.savePart(any(), any(), any()), () -> conventionService.deleteParts(any(), any(), anyCollection()), conventions, this::convention,
                row -> row.getRelativePath() == null);
        stubParts(() -> scriptService.replaceVersion(any(), any(), anyList()), () -> scriptService.savePart(any(), any(), any()), () -> scriptService.deleteParts(any(), any(), anyCollection()), scripts, this::script, row -> true);
        stubParts(() -> referenceService.replaceVersion(any(), any(), anyList()), () -> referenceService.savePart(any(), any(), any()), () -> referenceService.deleteParts(any(), any(), anyCollection()), references, this::reference, row -> true);
        stubParts(() -> toolParameterService.replaceVersion(any(), any(), anyList()), () -> toolParameterService.savePart(any(), any(), any()), () -> toolParameterService.deleteParts(any(), any(), anyCollection()), toolParameters, this::toolParameter, row -> true);

        lenient().when(skillService.replaceVersion(any(), any(), anyList())).thenAnswer(invocation ->
                replace(skills, invocation.<List<SkillIndex>>getArgument(2), this::skill));
        lenient().when(skillService.saveSkill(any(), any(), any())).thenAnswer(invocation -> {
            SkillIndex index = invocation.getArgument(2);
            skills.removeIf(row -> row.getName().equals(index.name()));
            return add(skills, skill(index));
        });
        lenient().when(skillService.deleteSkill(any(), any(), anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(2);
            return skills.removeIf(row -> row.getName().equals(name));
        });
    }

    // The replaceVersion, saveFile and deleteFile of a kind stored by path. The replaceVersion stub is
    // shared with the parts of workflow and convention, so it replaces every row of the kind.
    @SuppressWarnings("unchecked")
    private <E extends BaseEntity, I> void stubFiles(Supplier<Object> replaceCall, Supplier<Object> saveCall,
            Supplier<Object> deleteCall, List<E> rows,
                                                     Function<I, E> toEntity, Function<E, String> pathOf) {
        lenient().when(replaceCall.get()).thenAnswer(invocation -> replace(rows, (List<I>) invocation.getArgument(2), toEntity));
        lenient().when(saveCall.get()).thenAnswer(invocation -> {
            E entity = toEntity.apply((I) invocation.getArgument(2));
            rows.removeIf(row -> pathOf.apply(entity).equals(pathOf.apply(row)));
            return add(rows, entity);
        });
        lenient().when(deleteCall.get()).thenAnswer(invocation -> {
            String path = invocation.getArgument(2);
            return rows.removeIf(row -> path.equals(pathOf.apply(row)));
        });
    }

    @SuppressWarnings("unchecked")
    private <E extends BaseEntity, I> void stubParts(Supplier<Object> replaceCall, Supplier<Object> saveCall,
            Supplier<Object> deleteCall, List<E> rows,
                                                     Function<I, E> toEntity, Predicate<E> isPart) {
        lenient().when(replaceCall.get()).thenAnswer(invocation -> {
            List<E> saved = replace(rows, (List<I>) invocation.getArgument(2), toEntity);
            pruneLinks();
            return saved;
        });
        lenient().when(saveCall.get()).thenAnswer(invocation -> add(rows, toEntity.apply((I) invocation.getArgument(2))));
        lenient().when(deleteCall.get()).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(2);
            int before = rows.size();
            rows.removeIf(row -> isPart.test(row) && ids.contains(row.getId()));
            pruneLinks();
            return before - rows.size();
        });
    }

    // The database cascade from a part to the links of every skill.
    private void pruneLinks() {
        Set<UUID> workflowIds = idsOf(workflows);
        Set<UUID> scriptIds = idsOf(scripts);
        Set<UUID> conventionIds = idsOf(conventions);
        Set<UUID> referenceIds = idsOf(references);
        Set<UUID> toolParameterIds = idsOf(toolParameters);
        for (Skill skill : skills) {
            skill.getWorkflows().removeIf(link -> !workflowIds.contains(link.getWorkflowId()));
            skill.getScripts().removeIf(link -> !scriptIds.contains(link.getScriptId()));
            skill.getConventions().removeIf(link -> !conventionIds.contains(link.getConventionId()));
            skill.getReferences().removeIf(link -> !referenceIds.contains(link.getReferenceId()));
            skill.getToolParameters().removeIf(link -> !toolParameterIds.contains(link.getToolParameterId()));
        }
    }

    private static Set<UUID> idsOf(List<? extends BaseEntity> rows) {
        return Set.copyOf(rows.stream().map(BaseEntity::getId).toList());
    }

    private static <E, I> List<E> replace(List<E> rows, List<I> indexes, Function<I, E> toEntity) {
        rows.clear();
        List<E> saved = indexes.stream().map(toEntity).toList();
        rows.addAll(saved);
        return saved;
    }

    private static <E> E add(List<E> rows, E entity) {
        rows.add(entity);
        return entity;
    }

    /** A tool link of the store's version. */
    public ToolLink toolLink(ToolLinkName name) {
        return ToolLink.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId).name(name).build();
    }

    private AgentsFile agentsFile(AgentsFileIndex index) {
        return AgentsFile.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).placement(index.placement()).build();
    }

    private Rule rule(RuleIndex index) {
        Rule rule = Rule.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).relativePath(index.relativePath()).description(index.description())
                .paths(new ArrayList<>(index.paths())).alwaysOn(index.paths().isEmpty()).overridesPath(index.overridesPath())
                .build();
        index.conventionPaths().forEach(path -> rule.getConventions().add(
                RuleConvention.builder().rule(rule).conventionPath(path).build()));
        return rule;
    }

    private Guardrail guardrail(GuardrailIndex index) {
        return Guardrail.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).name(index.name()).category(index.category())
                .description(index.description()).deny(new ArrayList<>(index.deny())).build();
    }

    private Convention convention(ConventionIndex index) {
        return Convention.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).relativePath(index.relativePath()).title(index.title())
                .overridesPath(index.overridesPath()).build();
    }

    private Role role(RoleIndex index) {
        return Role.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).relativePath(index.relativePath()).title(index.title()).build();
    }

    private Workflow workflow(WorkflowIndex index) {
        return Workflow.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).relativePath(index.relativePath()).title(index.title()).build();
    }

    private Template template(TemplateIndex index) {
        return Template.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).relativePath(index.relativePath()).title(index.title()).build();
    }

    private Hook hook(HookIndex index) {
        return Hook.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).relativePath(index.relativePath()).executable(index.executable()).build();
    }

    private Subagent subagent(SubagentIndex index) {
        return Subagent.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).relativePath(index.relativePath()).name(index.name())
                .description(index.description()).model(index.model()).maxTurns(index.maxTurns()).tools(index.tools()).build();
    }

    private Script script(ScriptIndex index) {
        return Script.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).name(index.name()).executable(index.executable()).build();
    }

    private Reference reference(ReferenceIndex index) {
        return Reference.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).name(index.name()).mediaType(index.mediaType()).build();
    }

    private ToolParameter toolParameter(ToolParameterIndex index) {
        return ToolParameter.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).name(index.name()).format(index.format()).build();
    }

    private Skill skill(SkillIndex index) {
        Skill skill = Skill.builder().id(UUID.randomUUID()).organizationId(organizationId).versionId(versionId)
                .contentBlobId(index.contentBlobId()).name(index.name()).description(index.description()).build();
        for (SkillPartIndex part : index.parts()) {
            switch (part.kind()) {
                case WORKFLOW -> skill.getWorkflows().add(
                        SkillWorkflow.builder().skill(skill).workflowId(part.partId()).path(part.path()).build());
                case SCRIPT -> skill.getScripts().add(
                        SkillScript.builder().skill(skill).scriptId(part.partId()).path(part.path()).build());
                case CONVENTION -> skill.getConventions().add(
                        SkillConvention.builder().skill(skill).conventionId(part.partId()).path(part.path()).build());
                case REFERENCE -> skill.getReferences().add(
                        SkillReference.builder().skill(skill).referenceId(part.partId()).path(part.path()).build());
                case TOOL_PARAMETER -> skill.getToolParameters().add(
                        SkillToolParameter.builder().skill(skill).toolParameterId(part.partId()).path(part.path()).build());
            }
        }
        return skill;
    }

    /** The lower-case hex SHA-256 of some bytes. */
    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** UTF-8 bytes of a text. */
    public static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
