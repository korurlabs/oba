package com.korur.oba.apps.kaya.render.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.agentsfile.entity.AgentsFile;
import com.korur.oba.apps.kaya.agentsfile.service.AgentsFileQueryService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.convention.entity.Convention;
import com.korur.oba.apps.kaya.convention.service.ConventionQueryService;
import com.korur.oba.apps.kaya.guardrail.constant.GuardrailConstant;
import com.korur.oba.apps.kaya.guardrail.service.GuardrailQueryService;
import com.korur.oba.apps.kaya.hook.service.HookQueryService;
import com.korur.oba.apps.kaya.reference.entity.Reference;
import com.korur.oba.apps.kaya.reference.service.ReferenceQueryService;
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.constant.service.RenderServiceLogConstant;
import com.korur.oba.apps.kaya.render.dto.embedded.FileRefusal;
import com.korur.oba.apps.kaya.render.dto.embedded.PathMapping;
import com.korur.oba.apps.kaya.render.dto.embedded.PathMappingCount;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolMapping;
import com.korur.oba.apps.kaya.render.enums.AgentTool;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.helper.DeliveryPath;
import com.korur.oba.apps.kaya.render.helper.DeliveryPaths;
import com.korur.oba.apps.kaya.render.helper.IndexExtractor;
import com.korur.oba.apps.kaya.render.helper.RenderRefusals;
import com.korur.oba.apps.kaya.render.helper.SkillFolderParser;
import com.korur.oba.apps.kaya.render.helper.ToolPaths;
import com.korur.oba.apps.kaya.render.service.RenderQueryService;
import com.korur.oba.apps.kaya.role.service.RoleQueryService;
import com.korur.oba.apps.kaya.rule.service.RuleQueryService;
import com.korur.oba.apps.kaya.script.entity.Script;
import com.korur.oba.apps.kaya.script.service.ScriptQueryService;
import com.korur.oba.apps.kaya.settings.helper.SettingsParser;
import com.korur.oba.apps.kaya.settings.service.SettingsQueryService;
import com.korur.oba.apps.kaya.skill.entity.Skill;
import com.korur.oba.apps.kaya.skill.entity.SkillConvention;
import com.korur.oba.apps.kaya.skill.entity.SkillReference;
import com.korur.oba.apps.kaya.skill.entity.SkillScript;
import com.korur.oba.apps.kaya.skill.entity.SkillToolParameter;
import com.korur.oba.apps.kaya.skill.entity.SkillWorkflow;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.skill.service.SkillQueryService;
import com.korur.oba.apps.kaya.subagent.dto.embedded.SubagentIndex;
import com.korur.oba.apps.kaya.subagent.service.SubagentQueryService;
import com.korur.oba.apps.kaya.template.service.TemplateQueryService;
import com.korur.oba.apps.kaya.toollink.entity.ToolLink;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;
import com.korur.oba.apps.kaya.toollink.service.ToolLinkQueryService;
import com.korur.oba.apps.kaya.toolparameter.entity.ToolParameter;
import com.korur.oba.apps.kaya.toolparameter.service.ToolParameterQueryService;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;
import com.korur.oba.apps.kaya.workflow.service.WorkflowQueryService;

/**
 * The {@link RenderQueryService} implementation. It reads every kind of the version through its
 * Query service and loads all blobs in one query.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RenderQueryServiceImpl implements RenderQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RenderQueryServiceImpl.class);

    // The kinds whose rows render on their own; conventions and workflows are also parts of skills,
    // and a skill renders with its parts.
    private static final List<FileKind> STANDALONE_KINDS = List.of(
            FileKind.AGENTS_FILE, FileKind.RULE, FileKind.GUARDRAIL, FileKind.ROLE, FileKind.TEMPLATE, FileKind.HOOK,
            FileKind.SUBAGENT, FileKind.SETTINGS
    );

    // The kinds whose path mapping row counts the rows placeKind places: every kind with a folder or a
    // fixed file except the agents file, whose row depends on its placement.
    private static final List<FileKind> COUNTED_KINDS = List.of(
            FileKind.RULE, FileKind.GUARDRAIL, FileKind.CONVENTION, FileKind.ROLE, FileKind.WORKFLOW,
            FileKind.TEMPLATE, FileKind.HOOK, FileKind.SUBAGENT, FileKind.SETTINGS
    );

    private final AgentsFileQueryService agentsFileQueryService;
    private final RuleQueryService ruleQueryService;
    private final GuardrailQueryService guardrailQueryService;
    private final ConventionQueryService conventionQueryService;
    private final RoleQueryService roleQueryService;
    private final WorkflowQueryService workflowQueryService;
    private final TemplateQueryService templateQueryService;
    private final HookQueryService hookQueryService;
    private final SubagentQueryService subagentQueryService;
    private final SettingsQueryService settingsQueryService;
    private final ToolLinkQueryService toolLinkQueryService;
    private final SkillQueryService skillQueryService;
    private final ScriptQueryService scriptQueryService;
    private final ReferenceQueryService referenceQueryService;
    private final ToolParameterQueryService toolParameterQueryService;
    private final BlobQueryService blobQueryService;
    private final SettingsParser settingsParser;
    private final LoggingService loggingService;

    // One file before its blob is loaded, with the row it renders from.
    private record Placed(String path, FileMode mode, UUID blobId, String overridesPath, EntityKind entityKind, UUID entityId) {
    }

    @Override
    public List<RenderedFile> render(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.RENDERING_VERSION, versionId, organizationId);
        List<Convention> conventions = conventionQueryService.getEntitiesByVersionId(organizationId, versionId);
        List<Workflow> workflows = workflowQueryService.getEntitiesByVersionId(organizationId, versionId);
        List<Placed> placed = new ArrayList<>(placeConventions(conventions));
        placed.addAll(placeWorkflows(workflows));
        for (FileKind kind : STANDALONE_KINDS) {
            placed.addAll(placeKind(organizationId, versionId, kind));
        }
        List<Skill> skills = skillQueryService.getEntitiesByVersionId(organizationId, versionId);
        if (!skills.isEmpty()) {
            placeSkills(organizationId, versionId, skills, workflows, conventions, placed);
        }

        Map<UUID, Blob> blobs = blobsOf(organizationId, placed);
        List<RenderedFile> files = new ArrayList<>();
        for (Placed file : placed) {
            files.add(withContent(organizationId, versionId, file, blobs));
        }
        for (Skill skill : skills) {
            files.add(skillLink(skill.getName()));
        }
        for (ToolLink link : toolLinkQueryService.getEntitiesByVersionId(organizationId, versionId)) {
            files.add(toolLink(link.getName()));
        }
        files.sort(Comparator.comparing(RenderedFile::path));
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.VERSION_RENDERED, versionId, organizationId,
                files.size());
        return files;
    }

    @Override
    public Map<UUID, RenderedFile> renderPath(UUID organizationId, Collection<UUID> versionIds, String path) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.RENDERING_PATH, path, versionIds.size(),
                organizationId);
        Map<UUID, RenderedFile> files = new LinkedHashMap<>();
        Optional<DeliveryPath> delivery = DeliveryPaths.classify(path, null);
        if (delivery.isEmpty()) {
            return files;
        }
        DeliveryPath target = delivery.get();
        // AGENTS.md classifies as the root agents file; a version that holds the link there answers the link.
        Optional<ToolLinkName> toolLink = DeliveryPaths.toolLinkOf(path);
        Map<UUID, Placed> found = new LinkedHashMap<>();
        for (UUID versionId : new LinkedHashSet<>(versionIds)) {
            if (toolLink.isPresent()) {
                boolean linked = toolLinkQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                        .anyMatch(link -> link.getName() == toolLink.get());
                if (linked) {
                    files.put(versionId, toolLink(toolLink.get()));
                    continue;
                }
                if (target.kind() == FileKind.TOOL_LINK) {
                    continue;
                }
            }
            if (target.kind() == FileKind.SKILL_LINK) {
                if (!skillsNamed(organizationId, versionId, target.skillName()).isEmpty()) {
                    files.put(versionId, skillLink(target.skillName()));
                }
                continue;
            }
            List<Placed> candidates = target.kind() == FileKind.SKILL
                    ? placeSkill(organizationId, versionId, target)
                    : placeKind(organizationId, versionId, target.kind());
            candidates.stream().filter(file -> file.path().equals(path)).findFirst().ifPresent(file -> found.put(versionId, file));
        }
        Map<UUID, Blob> blobs = blobsOf(organizationId, found.values());
        found.forEach((versionId, file) -> files.put(versionId, withContent(organizationId, versionId, file, blobs)));
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.PATH_RENDERED, path, organizationId,
                files.size(), versionIds.size());
        return files;
    }

    @Override
    public List<PathMappingCount> getPathMappings(UUID organizationId, Collection<UUID> versionIds) {
        Map<PathMapping, Integer> counts = countMappings(organizationId, versionIds);
        return DeliveryPaths.mappings().stream()
                .map(mapping -> new PathMappingCount(mapping, counts == null ? null : counts.getOrDefault(mapping, 0)))
                .toList();
    }

    @Override
    public List<ToolMapping> getToolMappings(UUID organizationId, Collection<UUID> versionIds) {
        Map<PathMapping, Integer> counts = countMappings(organizationId, versionIds);
        return Arrays.stream(AgentTool.values())
                .map(tool -> new ToolMapping(tool, ToolPaths.pathsOf(tool).stream().map(path -> ToolPaths.count(path, counts)).toList()))
                .toList();
    }

    // The count of every path mapping row in the versions, read once per request: agents files and
    // the counted kinds per version, skills, their parts and the tool links for all versions at once.
    // Null when no version is asked for, so the rows show no count.
    private Map<PathMapping, Integer> countMappings(UUID organizationId, Collection<UUID> versionIds) {
        if (versionIds.isEmpty()) {
            return null;
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.COUNTING_PATH_MAPPINGS, versionIds.size(),
                organizationId);
        Set<UUID> distinctIds = new LinkedHashSet<>(versionIds);
        Map<PathMapping, Integer> counts = new HashMap<>();
        for (UUID versionId : distinctIds) {
            countVersion(organizationId, versionId, counts);
        }
        countSkills(organizationId, distinctIds, counts);
        countToolLinks(organizationId, distinctIds, counts);
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.PATH_MAPPINGS_COUNTED,
                counts.values().stream().mapToInt(Integer::intValue).sum(), versionIds.size(), organizationId);
        return counts;
    }

    // Counts the rows render would place outside skill directories, without their blobs. A version of
    // another organization reads no rows, so it adds nothing.
    private void countVersion(UUID organizationId, UUID versionId, Map<PathMapping, Integer> counts) {
        for (AgentsFile agentsFile : agentsFileQueryService.getEntitiesByVersionId(organizationId, versionId)) {
            counts.merge(DeliveryPaths.mappingOf(FileKind.AGENTS_FILE, null, agentsFile.getPlacement(), null), 1, Integer::sum);
        }
        for (FileKind kind : COUNTED_KINDS) {
            counts.merge(DeliveryPaths.mappingOf(kind, null, null, null), placeKind(organizationId, versionId, kind).size(),
                    Integer::sum);
        }
    }

    // Skills and their links are counted for all versions at once, so the cost does not grow with the
    // number of skills: a skill delivers its SKILL.md and its link, and every link one file in its
    // directory.
    private void countSkills(UUID organizationId, Set<UUID> versionIds, Map<PathMapping, Integer> counts) {
        int skills = skillQueryService.countByVersionIds(organizationId, versionIds);
        counts.merge(DeliveryPaths.mappingOf(FileKind.SKILL, null, null, null), skills, Integer::sum);
        counts.merge(DeliveryPaths.mappingOf(FileKind.SKILL_LINK, null, null, null), skills, Integer::sum);
        skillQueryService.countPartLinksByVersionIds(organizationId, versionIds)
                .forEach((part, links) -> counts.merge(partMapping(part), links, Integer::sum));
    }

    // The tool links of all versions in one query, so they add no statement per version.
    private void countToolLinks(UUID organizationId, Set<UUID> versionIds, Map<PathMapping, Integer> counts) {
        for (ToolLink link : toolLinkQueryService.getEntitiesByVersionIds(organizationId, versionIds)) {
            counts.merge(DeliveryPaths.mappingOf(FileKind.TOOL_LINK, null, null, link.getName()), 1, Integer::sum);
        }
    }

    private static PathMapping partMapping(SkillPartKind part) {
        return DeliveryPaths.mappingOf(FileKind.SKILL, part, null, null);
    }

    /**
     * Places every row of one kind that renders on its own: a kind of {@link #STANDALONE_KINDS}, a
     * convention or a workflow. Conventions and workflows that only a skill links have no path of
     * their own and are placed with the skill.
     */
    private List<Placed> placeKind(UUID organizationId, UUID versionId, FileKind kind) {
        return switch (kind) {
            case AGENTS_FILE -> agentsFileQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                    .map(RenderQueryServiceImpl::agentsFile)
                    .toList();
            case RULE -> ruleQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                    .map(rule -> inFolder(FileKind.RULE, rule.getRelativePath(), FileMode.FILE, rule.getContentBlobId(),
                            rule.getOverridesPath(), rule.getId()))
                    .toList();
            case GUARDRAIL -> guardrailQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                    .map(guardrail -> inFolder(FileKind.GUARDRAIL, guardrail.getName() + GuardrailConstant.FILE_SUFFIX,
                            FileMode.FILE, guardrail.getContentBlobId(), null, guardrail.getId()))
                    .toList();
            case CONVENTION -> placeConventions(conventionQueryService.getEntitiesByVersionId(organizationId, versionId));
            case ROLE -> roleQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                    .map(role -> inFolder(FileKind.ROLE, role.getRelativePath(), FileMode.FILE, role.getContentBlobId(), null,
                            role.getId()))
                    .toList();
            case WORKFLOW -> placeWorkflows(workflowQueryService.getEntitiesByVersionId(organizationId, versionId));
            case TEMPLATE -> templateQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                    .map(template -> inFolder(FileKind.TEMPLATE, template.getRelativePath(), FileMode.FILE,
                            template.getContentBlobId(), null, template.getId()))
                    .toList();
            case HOOK -> hookQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                    .map(hook -> inFolder(FileKind.HOOK, hook.getRelativePath(), modeOf(hook.getExecutable()),
                            hook.getContentBlobId(), null, hook.getId()))
                    .toList();
            case SUBAGENT -> subagentQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                    .map(subagent -> inFolder(FileKind.SUBAGENT, subagent.getRelativePath(), FileMode.FILE,
                            subagent.getContentBlobId(), null, subagent.getId()))
                    .toList();
            case SETTINGS -> settingsQueryService.getEntityByVersionId(organizationId, versionId).stream()
                    .map(settings -> new Placed(RenderConstant.SETTINGS_FILE, FileMode.FILE, settings.getContentBlobId(), null,
                            EntityKind.SETTINGS, settings.getId()))
                    .toList();
            case SKILL, SKILL_LINK, TOOL_LINK -> throw new IllegalArgumentException(kind.name());
        };
    }

    private static Placed agentsFile(AgentsFile agentsFile) {
        // Stored agents files keep their repository paths; only a composition moves one into a mount folder.
        String path = DeliveryPaths.agentsFile(agentsFile.getPlacement(), null)
                .orElseThrow(() -> RenderRefusals.notDeliverable(RenderConstant.AGENTS_FILE, RenderConstant.NOT_DELIVERABLE_REASON));
        return new Placed(path, FileMode.FILE, agentsFile.getContentBlobId(), null, EntityKind.AGENTS_FILE, agentsFile.getId());
    }

    private static List<Placed> placeConventions(List<Convention> conventions) {
        return conventions.stream()
                .filter(convention -> convention.getRelativePath() != null)
                .map(convention -> inFolder(FileKind.CONVENTION, convention.getRelativePath(), FileMode.FILE,
                        convention.getContentBlobId(), convention.getOverridesPath(), convention.getId()))
                .toList();
    }

    private static List<Placed> placeWorkflows(List<Workflow> workflows) {
        return workflows.stream()
                .filter(workflow -> workflow.getRelativePath() != null)
                .map(workflow -> inFolder(FileKind.WORKFLOW, workflow.getRelativePath(), FileMode.FILE, workflow.getContentBlobId(),
                        null, workflow.getId()))
                .toList();
    }

    // A path below a skill directory: its SKILL.md alone, or the skill with its parts.
    private List<Placed> placeSkill(UUID organizationId, UUID versionId, DeliveryPath target) {
        List<Skill> skills = skillsNamed(organizationId, versionId, target.skillName());
        if (skills.isEmpty()) {
            return List.of();
        }
        if (target.relativePath().equals(RenderConstant.SKILL_FILE)) {
            return List.of(skillFile(skills.getFirst()));
        }
        List<Placed> placed = new ArrayList<>();
        placeSkills(organizationId, versionId, skills, workflowQueryService.getEntitiesByVersionId(organizationId, versionId),
                conventionQueryService.getEntitiesByVersionId(organizationId, versionId), placed);
        return placed;
    }

    private List<Skill> skillsNamed(UUID organizationId, UUID versionId, String name) {
        return skillQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                .filter(skill -> skill.getName().equals(name))
                .toList();
    }

    // Each link renders its part's blob in the skill's directory, so a shared part appears in every
    // skill that links it.
    private void placeSkills(UUID organizationId, UUID versionId, List<Skill> skills, List<Workflow> workflows,
                             List<Convention> conventions, List<Placed> placed) {
        Map<UUID, UUID> workflowBlobs = workflows.stream().collect(Collectors.toMap(Workflow::getId, Workflow::getContentBlobId));
        Map<UUID, UUID> conventionBlobs = conventions.stream()
                .collect(Collectors.toMap(Convention::getId, Convention::getContentBlobId));
        Map<UUID, Script> scripts = new HashMap<>();
        scriptQueryService.getEntitiesByVersionId(organizationId, versionId).forEach(script -> scripts.put(script.getId(), script));
        Map<UUID, UUID> referenceBlobs = referenceQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                .collect(Collectors.toMap(Reference::getId, Reference::getContentBlobId));
        Map<UUID, UUID> toolParameterBlobs = toolParameterQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                .collect(Collectors.toMap(ToolParameter::getId, ToolParameter::getContentBlobId));
        for (Skill skill : skills) {
            String directory = DeliveryPaths.skillDirectory(skill.getName());
            placed.add(skillFile(skill));
            for (SkillWorkflow link : skill.getWorkflows()) {
                placed.add(new Placed(directory + link.getPath(), FileMode.FILE, workflowBlobs.get(link.getWorkflowId()), null,
                        EntityKind.WORKFLOW, link.getWorkflowId()));
            }
            for (SkillScript link : skill.getScripts()) {
                Script script = scripts.get(link.getScriptId());
                placed.add(new Placed(directory + link.getPath(), modeOf(script.getExecutable()), script.getContentBlobId(), null,
                        EntityKind.SCRIPT, script.getId()));
            }
            for (SkillConvention link : skill.getConventions()) {
                placed.add(new Placed(directory + link.getPath(), FileMode.FILE, conventionBlobs.get(link.getConventionId()), null,
                        EntityKind.CONVENTION, link.getConventionId()));
            }
            for (SkillReference link : skill.getReferences()) {
                placed.add(new Placed(directory + link.getPath(), FileMode.FILE, referenceBlobs.get(link.getReferenceId()), null,
                        EntityKind.REFERENCE, link.getReferenceId()));
            }
            for (SkillToolParameter link : skill.getToolParameters()) {
                placed.add(new Placed(directory + link.getPath(), FileMode.FILE,
                        toolParameterBlobs.get(link.getToolParameterId()), null, EntityKind.TOOL_PARAMETER,
                        link.getToolParameterId()));
            }
        }
    }

    private static Placed skillFile(Skill skill) {
        return new Placed(DeliveryPaths.skillDirectory(skill.getName()) + RenderConstant.SKILL_FILE, FileMode.FILE,
                skill.getContentBlobId(), null, EntityKind.SKILL, skill.getId());
    }

    private static RenderedFile skillLink(String name) {
        return new RenderedFile(DeliveryPaths.skillLink(name), FileMode.SYMLINK, DeliveryPaths.skillLinkTarget(name), null, null,
                null, null);
    }

    private static RenderedFile toolLink(ToolLinkName name) {
        return new RenderedFile(DeliveryPaths.toolLinkPath(name), FileMode.SYMLINK, DeliveryPaths.toolLinkTarget(name), null, null,
                null, null);
    }

    private static Placed inFolder(FileKind kind, String relativePath, FileMode mode, UUID blobId, String overridesPath,
                                   UUID entityId) {
        return new Placed(DeliveryPaths.folderOf(kind) + relativePath, mode, blobId, overridesPath, EntityKind.valueOf(kind.name()),
                entityId);
    }

    private static FileMode modeOf(Boolean executable) {
        return Boolean.TRUE.equals(executable) ? FileMode.EXECUTABLE : FileMode.FILE;
    }

    // All blobs of the placed files in one query.
    private Map<UUID, Blob> blobsOf(UUID organizationId, Collection<Placed> placed) {
        Set<UUID> blobIds = placed.stream().map(Placed::blobId).collect(Collectors.toSet());
        return blobIds.isEmpty() ? Map.of() : blobQueryService.getEntitiesByIds(organizationId, blobIds);
    }

    private RenderedFile withContent(UUID organizationId, UUID versionId, Placed file, Map<UUID, Blob> blobs) {
        Blob blob = blobs.get(file.blobId());
        if (blob == null) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.BLOB_MISSING, versionId, organizationId,
                    file.blobId());
            throw new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(RenderConstant.FIELD_PATH, file.path()));
        }
        return new RenderedFile(file.path(), file.mode(), null, blob.getContent(), blob.getSha256(), blob.getId(),
                file.overridesPath(), file.entityKind(), file.entityId());
    }

    @Override
    public List<FileRefusal> screen(List<SourceFile> files) {
        Map<String, FileRefusal> refused = new LinkedHashMap<>();
        Map<String, List<SourceFile>> skillFiles = new TreeMap<>();
        Map<String, SourceFile> skillLinks = new TreeMap<>();
        Map<String, String> subagentPaths = new HashMap<>();
        for (SourceFile file : files) {
            try {
                DeliveryPath delivery = DeliveryPaths.requireDeliverable(file, null);
                switch (delivery.kind()) {
                    case AGENTS_FILE -> IndexExtractor.agentsFile(delivery);
                    case RULE -> IndexExtractor.rule(delivery, file);
                    case GUARDRAIL -> IndexExtractor.guardrail(delivery, file);
                    case CONVENTION -> IndexExtractor.convention(delivery, file);
                    case ROLE -> IndexExtractor.role(delivery, file);
                    case WORKFLOW -> IndexExtractor.workflow(delivery, file);
                    case TEMPLATE -> IndexExtractor.template(delivery, file);
                    case HOOK -> IndexExtractor.hook(delivery, file);
                    case SUBAGENT -> requireFreeSubagentName(delivery, file, subagentPaths);
                    case SETTINGS -> requireSettingsObject(file);
                    case SKILL -> skillFiles.computeIfAbsent(delivery.skillName(), name -> new ArrayList<>()).add(file);
                    case SKILL_LINK -> skillLinks.put(delivery.skillName(), file);
                    case TOOL_LINK -> {
                        // requireDeliverable has held the link to its fixed target; whether that target
                        // is delivered is the composition's symlink rule.
                    }
                }
            } catch (ValidationException refusal) {
                // The refusal is the result: the caller reports the file and leaves it out.
                refused.put(file.path(), refusalOf(file.path(), refusal));
            }
        }
        for (Map.Entry<String, List<SourceFile>> folder : skillFiles.entrySet()) {
            try {
                SkillFolderParser.parse(folder.getKey(), folder.getValue());
            } catch (ValidationException refusal) {
                // A skill is stored whole or not at all, so every file of its directory goes with it.
                folder.getValue().forEach(file -> refused.put(file.path(), refusalOf(file.path(), refusal)));
            }
        }
        for (Map.Entry<String, SourceFile> link : skillLinks.entrySet()) {
            String name = link.getKey();
            boolean skillKept = skillFiles.containsKey(name)
                    && skillFiles.get(name).stream().noneMatch(file -> refused.containsKey(file.path()));
            if (!skillKept) {
                String path = link.getValue().path();
                refused.put(path, refusalOf(path, RenderRefusals.invalidFile(path,
                        String.format(RenderConstant.SKILL_WITHOUT_SKILL_FILE_REASON, DeliveryPaths.skillDirectory(name)))));
            }
        }
        List<FileRefusal> refusals = files.stream()
                .map(file -> refused.get(file.path()))
                .filter(Objects::nonNull)
                .toList();
        refusals.forEach(refusal -> loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.FILE_SCREENED_OUT,
                refusal.path(), files.size(), refusal.errorCode(), refusal.reason()));
        return refusals;
    }

    @Override
    public Map<EntityKind, Integer> countEntities(List<SourceFile> files) {
        Map<EntityKind, Integer> counts = new EnumMap<>(EntityKind.class);
        for (EntityKind kind : EntityKind.values()) {
            counts.put(kind, 0);
        }
        for (SourceFile file : files) {
            Optional<DeliveryPath> delivery = DeliveryPaths.classify(file.path(), null, file.isSymlink());
            if (delivery.isEmpty() || delivery.get().entityKind(file.mode()) == null) {
                continue;
            }
            counts.merge(delivery.get().entityKind(file.mode()), 1, Integer::sum);
        }
        return counts;
    }

    // The subagent name is unique per version, as the table requires.
    private static void requireFreeSubagentName(DeliveryPath delivery, SourceFile file, Map<String, String> subagentPaths) {
        SubagentIndex index = IndexExtractor.subagent(delivery, file).apply(null);
        String taken = subagentPaths.putIfAbsent(index.name(), file.path());
        if (taken != null) {
            throw RenderRefusals.invalidFile(file.path(), String.format(RenderConstant.SUBAGENT_NAME_TAKEN_REASON, index.name(), taken));
        }
    }

    private void requireSettingsObject(SourceFile file) {
        if (settingsParser.parse(file.content()).isEmpty()) {
            throw RenderRefusals.invalidFile(file.path(), RenderConstant.SETTINGS_NOT_OBJECT_REASON);
        }
    }

    private static FileRefusal refusalOf(String path, ValidationException refusal) {
        Object reason = refusal.getErrorValues() == null ? null : refusal.getErrorValues().get(RenderConstant.FIELD_REASON);
        return new FileRefusal(path, refusal.getErrorCode(), reason == null ? refusal.getErrorMessage() : reason.toString());
    }
}
