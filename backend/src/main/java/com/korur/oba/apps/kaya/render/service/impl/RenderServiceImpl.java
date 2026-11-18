package com.korur.oba.apps.kaya.render.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.agentsfile.dto.embedded.AgentsFileIndex;
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.agentsfile.service.AgentsFileService;
import com.korur.oba.apps.kaya.blob.service.BlobService;
import com.korur.oba.apps.kaya.convention.dto.embedded.ConventionIndex;
import com.korur.oba.apps.kaya.convention.entity.Convention;
import com.korur.oba.apps.kaya.convention.service.ConventionQueryService;
import com.korur.oba.apps.kaya.convention.service.ConventionService;
import com.korur.oba.apps.kaya.guardrail.dto.embedded.GuardrailIndex;
import com.korur.oba.apps.kaya.guardrail.service.GuardrailService;
import com.korur.oba.apps.kaya.hook.dto.embedded.HookIndex;
import com.korur.oba.apps.kaya.hook.service.HookService;
import com.korur.oba.apps.kaya.reference.dto.embedded.ReferenceIndex;
import com.korur.oba.apps.kaya.reference.entity.Reference;
import com.korur.oba.apps.kaya.reference.service.ReferenceQueryService;
import com.korur.oba.apps.kaya.reference.service.ReferenceService;
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.constant.service.RenderServiceLogConstant;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SkillFolder;
import com.korur.oba.apps.kaya.render.dto.embedded.SkillPartFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.render.helper.DeliveryPath;
import com.korur.oba.apps.kaya.render.helper.DeliveryPaths;
import com.korur.oba.apps.kaya.render.helper.FileCandidates;
import com.korur.oba.apps.kaya.render.helper.IndexExtractor;
import com.korur.oba.apps.kaya.render.helper.RenderRefusals;
import com.korur.oba.apps.kaya.render.helper.SkillFolderParser;
import com.korur.oba.apps.kaya.render.service.RenderQueryService;
import com.korur.oba.apps.kaya.render.service.RenderService;
import com.korur.oba.apps.kaya.role.dto.embedded.RoleIndex;
import com.korur.oba.apps.kaya.role.service.RoleService;
import com.korur.oba.apps.kaya.rule.dto.embedded.RuleIndex;
import com.korur.oba.apps.kaya.rule.service.RuleService;
import com.korur.oba.apps.kaya.script.dto.embedded.ScriptIndex;
import com.korur.oba.apps.kaya.script.entity.Script;
import com.korur.oba.apps.kaya.script.service.ScriptQueryService;
import com.korur.oba.apps.kaya.script.service.ScriptService;
import com.korur.oba.apps.kaya.settings.dto.embedded.SettingsIndex;
import com.korur.oba.apps.kaya.settings.helper.SettingsParser;
import com.korur.oba.apps.kaya.settings.service.SettingsService;
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillIndex;
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillPartIndex;
import com.korur.oba.apps.kaya.skill.entity.Skill;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.skill.service.SkillQueryService;
import com.korur.oba.apps.kaya.skill.service.SkillService;
import com.korur.oba.apps.kaya.subagent.dto.embedded.SubagentIndex;
import com.korur.oba.apps.kaya.subagent.entity.Subagent;
import com.korur.oba.apps.kaya.subagent.service.SubagentQueryService;
import com.korur.oba.apps.kaya.subagent.service.SubagentService;
import com.korur.oba.apps.kaya.template.dto.embedded.TemplateIndex;
import com.korur.oba.apps.kaya.template.service.TemplateService;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;
import com.korur.oba.apps.kaya.toollink.service.ToolLinkService;
import com.korur.oba.apps.kaya.toolparameter.dto.embedded.ToolParameterIndex;
import com.korur.oba.apps.kaya.toolparameter.entity.ToolParameter;
import com.korur.oba.apps.kaya.toolparameter.service.ToolParameterQueryService;
import com.korur.oba.apps.kaya.toolparameter.service.ToolParameterService;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.helper.FileCandidate;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.workflow.dto.embedded.WorkflowIndex;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;
import com.korur.oba.apps.kaya.workflow.service.WorkflowQueryService;
import com.korur.oba.apps.kaya.workflow.service.WorkflowService;

/**
 * The {@link RenderService} implementation. Every call checks all its input first (path, delivery
 * rules through {@link ValidationQueryService}, index fields), then stores the blobs and writes the
 * kind rows through each kind's Command service. Parts are written before the skills that link them,
 * because replacing a part deletes its links.
 */
@Service
@RequiredArgsConstructor
public class RenderServiceImpl implements RenderService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RenderServiceImpl.class);

    private final RenderQueryService renderQueryService;
    private final ValidationQueryService validationQueryService;
    private final BlobService blobService;
    private final SettingsParser settingsParser;
    private final AgentsFileService agentsFileService;
    private final RuleService ruleService;
    private final GuardrailService guardrailService;
    private final ConventionService conventionService;
    private final ConventionQueryService conventionQueryService;
    private final RoleService roleService;
    private final WorkflowService workflowService;
    private final WorkflowQueryService workflowQueryService;
    private final TemplateService templateService;
    private final HookService hookService;
    private final SubagentService subagentService;
    private final SubagentQueryService subagentQueryService;
    private final SettingsService settingsService;
    private final ToolLinkService toolLinkService;
    private final ScriptService scriptService;
    private final ScriptQueryService scriptQueryService;
    private final ReferenceService referenceService;
    private final ReferenceQueryService referenceQueryService;
    private final ToolParameterService toolParameterService;
    private final ToolParameterQueryService toolParameterQueryService;
    private final SkillService skillService;
    private final SkillQueryService skillQueryService;
    private final LoggingService loggingService;

    // A file with content whose index is built once its blob is stored.
    private record Pending<T>(byte[] content, Function<UUID, T> index) {
    }

    // The path and name of a file whose kind keeps its names unique per version.
    private record NamedFile(String relativePath, String name) {

        static NamedFile of(SubagentIndex index) {
            return new NamedFile(index.relativePath(), index.name());
        }

        static NamedFile of(Subagent subagent) {
            return new NamedFile(subagent.getRelativePath(), subagent.getName());
        }
    }

    // The parts two skills share: same kind, file name, bytes and executable bit. Workflow and
    // convention rows store no name, so for them the bytes alone decide.
    private record PartKey(SkillPartKind kind, String name, UUID blobId, boolean executable) {

        static PartKey of(SkillPartKind kind, String name, UUID blobId, boolean executable) {
            boolean named = kind != SkillPartKind.WORKFLOW && kind != SkillPartKind.CONVENTION;
            return new PartKey(kind, named ? name : null, blobId, executable);
        }
    }

    // A skill as read before any write, so later deletes cannot detach what is still needed.
    private record SkillSnapshot(String name, UUID contentBlobId, String description, List<SkillPartIndex> links) {
    }

    // Every pending index of a whole file tree, by kind.
    private static final class Tree {
        private final List<Pending<AgentsFileIndex>> agentsFiles = new ArrayList<>();
        private final List<Pending<RuleIndex>> rules = new ArrayList<>();
        private final List<Pending<GuardrailIndex>> guardrails = new ArrayList<>();
        private final List<Pending<ConventionIndex>> conventions = new ArrayList<>();
        private final List<Pending<RoleIndex>> roles = new ArrayList<>();
        private final List<Pending<WorkflowIndex>> workflows = new ArrayList<>();
        private final List<Pending<TemplateIndex>> templates = new ArrayList<>();
        private final List<Pending<HookIndex>> hooks = new ArrayList<>();
        private final List<Pending<SubagentIndex>> subagents = new ArrayList<>();
        private final List<SkillFolder> skills = new ArrayList<>();
        private byte[] settings;
        private final Set<ToolLinkName> toolLinks = EnumSet.noneOf(ToolLinkName.class);
    }

    @Override
    @Transactional
    public void parse(UUID organizationId, UUID versionId, List<SourceFile> files) {
        parseTree(organizationId, versionId, files, false, Map.of());
    }

    @Override
    @Transactional
    public void parseChange(UUID organizationId, UUID versionId, List<SourceFile> files,
                            List<RenderedFile> base) {
        parseTree(organizationId, versionId, files, false, byPath(base));
    }

    @Override
    @Transactional
    public void parseImported(UUID organizationId, UUID versionId, List<SourceFile> files) {
        parseTree(organizationId, versionId, files, true, Map.of());
    }

    // An imported tree mirrors a repository as it is, so only the size limits are left to its caller.
    private void parseTree(UUID organizationId, UUID versionId, List<SourceFile> files,
                           boolean imported, Map<String, RenderedFile> base) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.PARSING_VERSION, files.size(), versionId,
                organizationId);
        Map<String, DeliveryPath> deliveries = new LinkedHashMap<>();
        for (SourceFile file : files) {
            if (file.isRemoval()) {
                throw RenderRefusals.invalidFile(file.path(), RenderConstant.REMOVAL_IN_TREE_REASON);
            }
            if (deliveries.containsKey(file.path())) {
                throw RenderRefusals.invalidFile(file.path(), RenderConstant.PATH_TWICE_REASON);
            }
            deliveries.put(file.path(), DeliveryPaths.requireDeliverable(file, null));
        }
        requireValid(organizationId, versionId, files.stream().map(RenderServiceImpl::candidateOf).toList(), files,
                imported, base);

        Tree tree = new Tree();
        Map<String, List<SourceFile>> skillFiles = new TreeMap<>();
        Set<String> skillLinks = new HashSet<>();
        for (SourceFile file : files) {
            DeliveryPath delivery = deliveries.get(file.path());
            switch (delivery.kind()) {
                case AGENTS_FILE -> tree.agentsFiles.add(new Pending<>(file.content(), IndexExtractor.agentsFile(delivery)));
                case RULE -> tree.rules.add(new Pending<>(file.content(), IndexExtractor.rule(delivery, file)));
                case GUARDRAIL -> tree.guardrails.add(new Pending<>(file.content(), IndexExtractor.guardrail(delivery, file)));
                case CONVENTION -> tree.conventions.add(new Pending<>(file.content(), IndexExtractor.convention(delivery, file)));
                case ROLE -> tree.roles.add(new Pending<>(file.content(), IndexExtractor.role(delivery, file)));
                case WORKFLOW -> tree.workflows.add(new Pending<>(file.content(), IndexExtractor.workflow(delivery, file)));
                case TEMPLATE -> tree.templates.add(new Pending<>(file.content(), IndexExtractor.template(delivery, file)));
                case HOOK -> tree.hooks.add(new Pending<>(file.content(), IndexExtractor.hook(delivery, file)));
                case SUBAGENT -> tree.subagents.add(new Pending<>(file.content(), IndexExtractor.subagent(delivery, file)));
                case SETTINGS -> tree.settings = requireSettings(file);
                case SKILL -> skillFiles.computeIfAbsent(delivery.skillName(), name -> new ArrayList<>()).add(file);
                case SKILL_LINK -> skillLinks.add(delivery.skillName());
                case TOOL_LINK -> tree.toolLinks.add(delivery.toolLink());
            }
        }
        skillFiles.forEach((name, folder) -> tree.skills.add(SkillFolderParser.parse(name, folder)));
        for (String name : skillLinks) {
            if (!skillFiles.containsKey(name)) {
                throw RenderRefusals.invalidFile(DeliveryPaths.skillLink(name),
                        String.format(RenderConstant.SKILL_WITHOUT_SKILL_FILE_REASON, DeliveryPaths.skillDirectory(name)));
            }
        }
        requireUniqueNames(FileKind.SUBAGENT, RenderConstant.SUBAGENT_NAME_TAKEN_REASON,
                tree.subagents.stream().map(pending -> NamedFile.of(pending.index().apply(null))).toList(), List.of());

        write(organizationId, versionId, tree);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.VERSION_PARSED, versionId, organizationId,
                files.size());
    }

    private void write(UUID organizationId, UUID versionId, Tree tree) {
        // Parts first: replacing them deletes every skill link, which the skills below write again.
        List<WorkflowIndex> workflows = new ArrayList<>(stored(organizationId, tree.workflows));
        List<ConventionIndex> conventions = new ArrayList<>(stored(organizationId, tree.conventions));
        List<ScriptIndex> scripts = new ArrayList<>();
        List<ReferenceIndex> references = new ArrayList<>();
        List<ToolParameterIndex> toolParameters = new ArrayList<>();
        Map<PartKey, Integer> positions = new HashMap<>();
        Map<SkillPartFile, PartKey> keys = new HashMap<>();
        Map<String, UUID> skillBlobs = new HashMap<>();
        for (SkillFolder skill : tree.skills) {
            skillBlobs.put(skill.name(), store(organizationId, skill.skillFile()));
            for (SkillPartFile part : skill.parts()) {
                UUID blobId = store(organizationId, part.content());
                PartKey key = PartKey.of(part.kind(), part.name(), blobId, part.executable());
                keys.put(part, key);
                if (positions.containsKey(key)) {
                    continue;
                }
                switch (part.kind()) {
                    case WORKFLOW -> {
                        positions.put(key, workflows.size());
                        workflows.add(IndexExtractor.workflowPart(part, blobId));
                    }
                    case CONVENTION -> {
                        positions.put(key, conventions.size());
                        conventions.add(IndexExtractor.conventionPart(part, blobId));
                    }
                    case SCRIPT -> {
                        positions.put(key, scripts.size());
                        scripts.add(IndexExtractor.scriptPart(part, blobId));
                    }
                    case REFERENCE -> {
                        positions.put(key, references.size());
                        references.add(IndexExtractor.referencePart(part, blobId));
                    }
                    case TOOL_PARAMETER -> {
                        positions.put(key, toolParameters.size());
                        toolParameters.add(IndexExtractor.toolParameterPart(part, blobId));
                    }
                }
            }
        }
        Map<SkillPartKind, List<? extends BaseEntity>> savedParts = new EnumMap<>(SkillPartKind.class);
        savedParts.put(SkillPartKind.WORKFLOW, workflowService.replaceVersion(organizationId, versionId, workflows));
        savedParts.put(SkillPartKind.CONVENTION, conventionService.replaceVersion(organizationId, versionId, conventions));
        savedParts.put(SkillPartKind.SCRIPT, scriptService.replaceVersion(organizationId, versionId, scripts));
        savedParts.put(SkillPartKind.REFERENCE, referenceService.replaceVersion(organizationId, versionId, references));
        savedParts.put(SkillPartKind.TOOL_PARAMETER, toolParameterService.replaceVersion(organizationId, versionId, toolParameters));

        List<SkillIndex> skills = new ArrayList<>();
        for (SkillFolder skill : tree.skills) {
            List<SkillPartIndex> links = new ArrayList<>();
            for (SkillPartFile part : skill.parts()) {
                PartKey key = keys.get(part);
                UUID partId = savedParts.get(part.kind()).get(positions.get(key)).getId();
                links.add(new SkillPartIndex(part.kind(), partId, part.path()));
            }
            skills.add(new SkillIndex(skill.name(), skillBlobs.get(skill.name()), skill.description(), links));
        }
        skillService.replaceVersion(organizationId, versionId, skills);
        ruleService.replaceVersion(organizationId, versionId, stored(organizationId, tree.rules));
        guardrailService.replaceVersion(organizationId, versionId, stored(organizationId, tree.guardrails));
        agentsFileService.replaceVersion(organizationId, versionId, stored(organizationId, tree.agentsFiles));
        roleService.replaceVersion(organizationId, versionId, stored(organizationId, tree.roles));
        templateService.replaceVersion(organizationId, versionId, stored(organizationId, tree.templates));
        hookService.replaceVersion(organizationId, versionId, stored(organizationId, tree.hooks));
        subagentService.replaceVersion(organizationId, versionId, stored(organizationId, tree.subagents));
        settingsService.replaceVersion(organizationId, versionId,
                tree.settings == null ? null : new SettingsIndex(store(organizationId, tree.settings)));
        toolLinkService.replaceVersion(organizationId, versionId, tree.toolLinks);
    }

    @Override
    @Transactional
    public void parseOne(UUID organizationId, UUID versionId, SourceFile file) {
        parseOneChange(organizationId, versionId, file, List.of());
    }

    @Override
    @Transactional
    public void parseOneChange(UUID organizationId, UUID versionId, SourceFile file,
                               List<RenderedFile> base) {
        DeliveryPath delivery = DeliveryPaths.requireDeliverable(file, null);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.PARSING_FILE,
                delivery.kind(), file.path(), versionId, organizationId);
        List<RenderedFile> current = null;
        if (!file.isRemoval() || delivery.kind() == FileKind.SKILL) {
            current = renderQueryService.render(organizationId, versionId);
        }
        if (!file.isRemoval()) {
            List<FileCandidate> candidates = new ArrayList<>();
            for (RenderedFile rendered : current) {
                if (!rendered.path().equals(file.path())) {
                    candidates.add(FileCandidates.of(rendered.path(), rendered.mode(), rendered.symlinkTarget(), null));
                }
            }
            candidates.add(candidateOf(file));
            requireValid(organizationId, versionId, candidates, List.of(file), false, byPath(base));
        }
        switch (delivery.kind()) {
            case AGENTS_FILE -> {
                if (file.isRemoval()) {
                    agentsFileService.deleteFile(organizationId, versionId, delivery.placement());
                } else {
                    agentsFileService.saveFile(organizationId, versionId, stored(organizationId, file, IndexExtractor.agentsFile(delivery)));
                }
                // The root AGENTS.md is a file or the link, never both; a removal has no mode, so it
                // removes whichever the version holds.
                if (delivery.placement() == AgentsFilePlacement.ROOT) {
                    toolLinkService.deleteLink(organizationId, versionId, ToolLinkName.ROOT_AGENTS_FILE);
                }
            }
            case RULE -> {
                if (file.isRemoval()) {
                    ruleService.deleteFile(organizationId, versionId, delivery.relativePath());
                } else {
                    ruleService.saveFile(organizationId, versionId, stored(organizationId, file, IndexExtractor.rule(delivery, file)));
                }
            }
            case GUARDRAIL -> {
                if (file.isRemoval()) {
                    guardrailService.deleteFile(organizationId, versionId, IndexExtractor.guardrailName(delivery));
                } else {
                    guardrailService.saveFile(organizationId, versionId,
                            stored(organizationId, file, IndexExtractor.guardrail(delivery, file)));
                }
            }
            case CONVENTION -> {
                if (file.isRemoval()) {
                    conventionService.deleteFile(organizationId, versionId, delivery.relativePath());
                } else {
                    conventionService.saveFile(organizationId, versionId,
                            stored(organizationId, file, IndexExtractor.convention(delivery, file)));
                }
            }
            case ROLE -> {
                if (file.isRemoval()) {
                    roleService.deleteFile(organizationId, versionId, delivery.relativePath());
                } else {
                    roleService.saveFile(organizationId, versionId, stored(organizationId, file, IndexExtractor.role(delivery, file)));
                }
            }
            case WORKFLOW -> {
                if (file.isRemoval()) {
                    workflowService.deleteFile(organizationId, versionId, delivery.relativePath());
                } else {
                    workflowService.saveFile(organizationId, versionId,
                            stored(organizationId, file, IndexExtractor.workflow(delivery, file)));
                }
            }
            case TEMPLATE -> {
                if (file.isRemoval()) {
                    templateService.deleteFile(organizationId, versionId, delivery.relativePath());
                } else {
                    templateService.saveFile(organizationId, versionId,
                            stored(organizationId, file, IndexExtractor.template(delivery, file)));
                }
            }
            case HOOK -> {
                if (file.isRemoval()) {
                    hookService.deleteFile(organizationId, versionId, delivery.relativePath());
                } else {
                    hookService.saveFile(organizationId, versionId, stored(organizationId, file, IndexExtractor.hook(delivery, file)));
                }
            }
            case SUBAGENT -> {
                if (file.isRemoval()) {
                    subagentService.deleteFile(organizationId, versionId, delivery.relativePath());
                } else {
                    Function<UUID, SubagentIndex> index = IndexExtractor.subagent(delivery, file);
                    requireUniqueNames(FileKind.SUBAGENT, RenderConstant.SUBAGENT_NAME_TAKEN_REASON,
                            List.of(NamedFile.of(index.apply(null))),
                            subagentQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                                    .map(NamedFile::of).toList());
                    subagentService.saveFile(organizationId, versionId, stored(organizationId, file, index));
                }
            }
            case SETTINGS -> settingsService.replaceVersion(organizationId, versionId,
                    file.isRemoval() ? null : new SettingsIndex(store(organizationId, requireSettings(file))));
            case SKILL_LINK -> requireSkillForLink(file, delivery, current);
            case SKILL -> replaceSkill(organizationId, versionId, delivery, file, current);
            case TOOL_LINK -> {
                if (file.isRemoval()) {
                    toolLinkService.deleteLink(organizationId, versionId, delivery.toolLink());
                } else {
                    toolLinkService.saveLink(organizationId, versionId, delivery.toolLink());
                    if (delivery.toolLink() == ToolLinkName.ROOT_AGENTS_FILE) {
                        agentsFileService.deleteFile(organizationId, versionId, AgentsFilePlacement.ROOT);
                    }
                }
            }
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.FILE_PARSED, delivery.kind(), file.path(),
                versionId, organizationId);
    }

    // The link is rendered from its skill and never stored; a save only checks that the skill exists.
    private static void requireSkillForLink(SourceFile file, DeliveryPath delivery, List<RenderedFile> current) {
        if (file.isRemoval()) {
            throw RenderRefusals.invalidFile(file.path(), RenderConstant.SKILL_LINK_DELETE_REASON);
        }
        String skillFile = DeliveryPaths.skillDirectory(delivery.skillName()) + RenderConstant.SKILL_FILE;
        boolean exists = current.stream().anyMatch(rendered -> rendered.path().equals(skillFile));
        if (!exists) {
            throw RenderRefusals.invalidFile(file.path(), String.format(RenderConstant.SKILL_WITHOUT_SKILL_FILE_REASON,
                    DeliveryPaths.skillDirectory(delivery.skillName())));
        }
    }

    /**
     * Re-parses the skill directory the file belongs to, with the file saved or removed. Unchanged
     * parts keep their rows; a saved part another skill links is relinked there; a part no skill links
     * any more is deleted.
     */
    private void replaceSkill(UUID organizationId, UUID versionId, DeliveryPath delivery, SourceFile file,
                              List<RenderedFile> current) {
        String name = delivery.skillName();
        String directory = DeliveryPaths.skillDirectory(name);
        List<SourceFile> folder = new ArrayList<>();
        for (RenderedFile rendered : current) {
            if (rendered.path().startsWith(directory) && !rendered.path().equals(file.path())) {
                folder.add(rendered.toSource());
            }
        }
        boolean removesSkill = file.isRemoval() && delivery.relativePath().equals(RenderConstant.SKILL_FILE);
        if (!file.isRemoval()) {
            folder.add(file);
        }
        SkillFolder parsed = removesSkill || folder.isEmpty() ? null : SkillFolderParser.parse(name, folder);

        List<SkillSnapshot> snapshots = skillQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                .map(RenderServiceImpl::snapshotOf)
                .toList();
        SkillSnapshot old = snapshots.stream().filter(skill -> skill.name().equals(name)).findFirst().orElse(null);
        List<SkillSnapshot> others = snapshots.stream().filter(skill -> !skill.name().equals(name)).toList();
        Map<SkillPartKind, Map<UUID, PartKey>> catalogue = partCatalogue(organizationId, versionId);

        List<SkillPartIndex> links = new ArrayList<>();
        if (parsed != null) {
            Map<PartKey, UUID> linked = new HashMap<>();
            for (SkillSnapshot skill : snapshots) {
                for (SkillPartIndex link : skill.links()) {
                    PartKey key = catalogue.get(link.kind()).get(link.partId());
                    if (key != null) {
                        linked.putIfAbsent(key, link.partId());
                    }
                }
            }
            for (SkillPartFile part : parsed.parts()) {
                UUID blobId = store(organizationId, part.content());
                PartKey key = PartKey.of(part.kind(), part.name(), blobId, part.executable());
                UUID partId = linked.get(key);
                if (partId == null) {
                    partId = savePart(organizationId, versionId, part, blobId);
                    linked.put(key, partId);
                }
                links.add(new SkillPartIndex(part.kind(), partId, part.path()));
            }
            skillService.saveSkill(organizationId, versionId,
                    new SkillIndex(name, store(organizationId, parsed.skillFile()), parsed.description(), links));
        } else if (old != null) {
            skillService.deleteSkill(organizationId, versionId, name);
        }

        // A saved part that other skills share moves with it, so every skill renders the new bytes.
        SkillPartIndex replaced = old == null || file.isRemoval() ? null : linkAt(old.links(), delivery.relativePath());
        SkillPartIndex replacement = linkAt(links, delivery.relativePath());
        List<SkillSnapshot> remaining = new ArrayList<>(others);
        if (replaced != null && replacement != null && replaced.kind() == replacement.kind()
                && !replaced.partId().equals(replacement.partId())) {
            remaining.clear();
            for (SkillSnapshot other : others) {
                SkillSnapshot relinked = relink(other, replaced, replacement.partId());
                if (relinked != other) {
                    skillService.saveSkill(organizationId, versionId,
                            new SkillIndex(relinked.name(), relinked.contentBlobId(), relinked.description(), relinked.links()));
                }
                remaining.add(relinked);
            }
        }

        Set<UUID> stillLinked = new HashSet<>();
        links.forEach(link -> stillLinked.add(link.partId()));
        remaining.forEach(skill -> skill.links().forEach(link -> stillLinked.add(link.partId())));
        Map<SkillPartKind, Set<UUID>> orphans = new EnumMap<>(SkillPartKind.class);
        if (old != null) {
            for (SkillPartIndex link : old.links()) {
                if (!stillLinked.contains(link.partId())) {
                    orphans.computeIfAbsent(link.kind(), kind -> new HashSet<>()).add(link.partId());
                }
            }
        }
        orphans.forEach((kind, partIds) -> deleteParts(organizationId, versionId, kind, partIds));
    }

    private static SkillPartIndex linkAt(List<SkillPartIndex> links, String path) {
        return links.stream().filter(link -> link.path().equals(path)).findFirst().orElse(null);
    }

    // The same skill when it does not link the part; otherwise a copy linking the replacement instead.
    private static SkillSnapshot relink(SkillSnapshot skill, SkillPartIndex replaced, UUID replacementId) {
        boolean links = skill.links().stream()
                .anyMatch(link -> link.kind() == replaced.kind() && link.partId().equals(replaced.partId()));
        if (!links) {
            return skill;
        }
        List<SkillPartIndex> relinked = skill.links().stream()
                .map(link -> link.kind() == replaced.kind() && link.partId().equals(replaced.partId())
                        ? new SkillPartIndex(link.kind(), replacementId, link.path())
                        : link)
                .toList();
        return new SkillSnapshot(skill.name(), skill.contentBlobId(), skill.description(), relinked);
    }

    private static SkillSnapshot snapshotOf(Skill skill) {
        List<SkillPartIndex> links = new ArrayList<>();
        skill.getWorkflows().forEach(link -> links.add(new SkillPartIndex(SkillPartKind.WORKFLOW, link.getWorkflowId(), link.getPath())));
        skill.getScripts().forEach(link -> links.add(new SkillPartIndex(SkillPartKind.SCRIPT, link.getScriptId(), link.getPath())));
        skill.getConventions().forEach(link ->
                links.add(new SkillPartIndex(SkillPartKind.CONVENTION, link.getConventionId(), link.getPath())));
        skill.getReferences().forEach(link ->
                links.add(new SkillPartIndex(SkillPartKind.REFERENCE, link.getReferenceId(), link.getPath())));
        skill.getToolParameters().forEach(link ->
                links.add(new SkillPartIndex(SkillPartKind.TOOL_PARAMETER, link.getToolParameterId(), link.getPath())));
        return new SkillSnapshot(skill.getName(), skill.getContentBlobId(), skill.getDescription(), links);
    }

    // The sharing key of every part row of the version, by kind and id.
    private Map<SkillPartKind, Map<UUID, PartKey>> partCatalogue(UUID organizationId, UUID versionId) {
        Map<SkillPartKind, Map<UUID, PartKey>> catalogue = new EnumMap<>(SkillPartKind.class);
        for (SkillPartKind kind : SkillPartKind.values()) {
            catalogue.put(kind, new HashMap<>());
        }
        for (Workflow workflow : workflowQueryService.getEntitiesByVersionId(organizationId, versionId)) {
            if (workflow.getRelativePath() == null) {
                catalogue.get(SkillPartKind.WORKFLOW).put(workflow.getId(),
                        PartKey.of(SkillPartKind.WORKFLOW, null, workflow.getContentBlobId(), false));
            }
        }
        for (Convention convention : conventionQueryService.getEntitiesByVersionId(organizationId, versionId)) {
            if (convention.getRelativePath() == null) {
                catalogue.get(SkillPartKind.CONVENTION).put(convention.getId(),
                        PartKey.of(SkillPartKind.CONVENTION, null, convention.getContentBlobId(), false));
            }
        }
        for (Script script : scriptQueryService.getEntitiesByVersionId(organizationId, versionId)) {
            catalogue.get(SkillPartKind.SCRIPT).put(script.getId(), PartKey.of(SkillPartKind.SCRIPT, script.getName(),
                    script.getContentBlobId(), Boolean.TRUE.equals(script.getExecutable())));
        }
        for (Reference reference : referenceQueryService.getEntitiesByVersionId(organizationId, versionId)) {
            catalogue.get(SkillPartKind.REFERENCE).put(reference.getId(),
                    PartKey.of(SkillPartKind.REFERENCE, reference.getName(), reference.getContentBlobId(), false));
        }
        for (ToolParameter toolParameter : toolParameterQueryService.getEntitiesByVersionId(organizationId, versionId)) {
            catalogue.get(SkillPartKind.TOOL_PARAMETER).put(toolParameter.getId(),
                    PartKey.of(SkillPartKind.TOOL_PARAMETER, toolParameter.getName(), toolParameter.getContentBlobId(), false));
        }
        return catalogue;
    }

    private UUID savePart(UUID organizationId, UUID versionId, SkillPartFile part, UUID blobId) {
        return switch (part.kind()) {
            case WORKFLOW -> workflowService.savePart(organizationId, versionId, IndexExtractor.workflowPart(part, blobId)).getId();
            case CONVENTION -> conventionService.savePart(organizationId, versionId, IndexExtractor.conventionPart(part, blobId)).getId();
            case SCRIPT -> scriptService.savePart(organizationId, versionId, IndexExtractor.scriptPart(part, blobId)).getId();
            case REFERENCE -> referenceService.savePart(organizationId, versionId, IndexExtractor.referencePart(part, blobId)).getId();
            case TOOL_PARAMETER -> toolParameterService.savePart(organizationId, versionId,
                    IndexExtractor.toolParameterPart(part, blobId)).getId();
        };
    }

    private void deleteParts(UUID organizationId, UUID versionId, SkillPartKind kind, Set<UUID> partIds) {
        switch (kind) {
            case WORKFLOW -> workflowService.deleteParts(organizationId, versionId, partIds);
            case CONVENTION -> conventionService.deleteParts(organizationId, versionId, partIds);
            case SCRIPT -> scriptService.deleteParts(organizationId, versionId, partIds);
            case REFERENCE -> referenceService.deleteParts(organizationId, versionId, partIds);
            case TOOL_PARAMETER -> toolParameterService.deleteParts(organizationId, versionId, partIds);
        }
    }

    /**
     * Refuses the files when any breaks a rule. A file whose path the base also holds is checked as a
     * change of the base's file, so a size limit the base already broke refuses only growth.
     */
    private void requireValid(UUID organizationId, UUID versionId, List<FileCandidate> linkCandidates, List<SourceFile> files,
                              boolean sizeLimitsAllowed, Map<String, RenderedFile> base) {
        List<Violation> violations = new ArrayList<>();
        for (SourceFile file : files) {
            RenderedFile before = base.get(file.path());
            violations.addAll(before == null
                    ? validationQueryService.validateFile(candidateOf(file))
                    : validationQueryService.validateFileChange(candidateOf(file), candidateOf(before.toSource())));
        }
        violations.addAll(validationQueryService.validateLinks(linkCandidates));
        if (sizeLimitsAllowed) {
            violations.removeIf(violation -> violation.rule().isSizeLimit());
        }
        if (!violations.isEmpty()) {
            String path = Objects.requireNonNullElse(violations.get(0).path(), files.get(0).path());
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, RenderServiceLogConstant.FILE_REFUSED, path, versionId, organizationId,
                    RenderConstant.VALIDATION_FAILED_REASON);
            throw RenderRefusals.violations(path, violations);
        }
    }

    private static Map<String, RenderedFile> byPath(List<RenderedFile> files) {
        Map<String, RenderedFile> byPath = new HashMap<>();
        files.forEach(file -> byPath.put(file.path(), file));
        return byPath;
    }

    private static FileCandidate candidateOf(SourceFile file) {
        return FileCandidates.of(file.path(), file.mode(), file.symlinkTarget(), file.content());
    }

    private byte[] requireSettings(SourceFile file) {
        if (settingsParser.parse(file.content()).isEmpty()) {
            throw RenderRefusals.invalidFile(file.path(), RenderConstant.SETTINGS_NOT_OBJECT_REASON);
        }
        return file.content();
    }

    /**
     * Refuses a saved file whose name another file of its kind in the version already has. Subagent
     * names are unique per version, as their table requires; without this check the unique key would
     * answer a save with a server error instead of a reason.
     *
     * @param kind     the kind, whose folder prefixes the paths in the refusal
     * @param reason   the refusal reason, formatted with the name and the path that holds it
     * @param saved    the files being saved
     * @param existing the stored files of the version; a stored file at a saved path is replaced, so
     *                 its name is free again
     */
    private static void requireUniqueNames(FileKind kind, String reason, List<NamedFile> saved, List<NamedFile> existing) {
        Map<String, String> pathsByName = new HashMap<>();
        Set<String> savedPaths = new HashSet<>();
        saved.forEach(file -> savedPaths.add(file.relativePath()));
        for (NamedFile file : existing) {
            if (!savedPaths.contains(file.relativePath())) {
                pathsByName.put(file.name(), file.relativePath());
            }
        }
        String folder = DeliveryPaths.folderOf(kind);
        for (NamedFile file : saved) {
            String taken = pathsByName.putIfAbsent(file.name(), file.relativePath());
            if (taken != null) {
                throw RenderRefusals.invalidFile(folder + file.relativePath(),
                        String.format(reason, file.name(), folder + taken));
            }
        }
    }

    private UUID store(UUID organizationId, byte[] content) {
        return blobService.storeContent(organizationId, content).getId();
    }

    private <T> T stored(UUID organizationId, SourceFile file, Function<UUID, T> index) {
        return index.apply(store(organizationId, file.content()));
    }

    private <T> List<T> stored(UUID organizationId, List<Pending<T>> pending) {
        return pending.stream().map(file -> file.index().apply(store(organizationId, file.content()))).toList();
    }
}
