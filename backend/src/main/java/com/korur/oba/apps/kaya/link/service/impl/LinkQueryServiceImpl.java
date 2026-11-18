package com.korur.oba.apps.kaya.link.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.convention.service.ConventionQueryService;
import com.korur.oba.apps.kaya.link.constant.LinkConstant;
import com.korur.oba.apps.kaya.link.constant.service.LinkServiceLogConstant;
import com.korur.oba.apps.kaya.link.dto.request.LinkQueryRequest;
import com.korur.oba.apps.kaya.link.dto.response.DependencyResponse;
import com.korur.oba.apps.kaya.link.dto.response.LinkResponse;
import com.korur.oba.apps.kaya.link.dto.response.embedded.DependencyEmbeddedResponse;
import com.korur.oba.apps.kaya.link.enums.DependencyRelation;
import com.korur.oba.apps.kaya.link.enums.LinkStatus;
import com.korur.oba.apps.kaya.link.mapper.LinkMapper;
import com.korur.oba.apps.kaya.link.service.LinkQueryService;
import com.korur.oba.apps.kaya.release.dto.embedded.ReleaseEntry;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.helper.ComposedFile;
import com.korur.oba.apps.kaya.release.helper.Composition;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.helper.DeliveryPath;
import com.korur.oba.apps.kaya.render.helper.DeliveryPaths;
import com.korur.oba.apps.kaya.render.helper.LinkCandidate;
import com.korur.oba.apps.kaya.render.helper.LinkExtractor;
import com.korur.oba.apps.kaya.rule.entity.Rule;
import com.korur.oba.apps.kaya.rule.service.RuleQueryService;
import com.korur.oba.apps.kaya.skill.dto.embedded.SkillPartLink;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.skill.service.SkillQueryService;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;
import com.korur.oba.apps.kaya.workflow.service.WorkflowQueryService;

/**
 * Implements {@link LinkQueryService}: resolves links against the latest release of a repository or
 * against a preview that composes a version, both through {@link ReleaseQueryService}. The
 * dependencies of one file add the relations the kinds store as an index: a rule's conventions
 * through {@link RuleQueryService} and a skill's parts through {@link SkillQueryService}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LinkQueryServiceImpl implements LinkQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LinkQueryServiceImpl.class);
    private static final Set<VersionStatus> OPEN_STATUSES = EnumSet.of(VersionStatus.DRAFT, VersionStatus.IN_REVIEW);
    private static final Comparator<DependencyEmbeddedResponse> DEPENDENCY_ORDER =
            Comparator.comparing(DependencyEmbeddedResponse::relation).thenComparing(DependencyEmbeddedResponse::path);

    /** A delivered file of the scope with the version that delivers it. */
    private record ScopeFile(UUID versionId, RenderedFile file) {
    }

    private final VersionQueryService versionQueryService;
    private final ReleaseQueryService releaseQueryService;
    private final BlobQueryService blobQueryService;
    private final RuleQueryService ruleQueryService;
    private final ConventionQueryService conventionQueryService;
    private final WorkflowQueryService workflowQueryService;
    private final SkillQueryService skillQueryService;
    private final LinkMapper linkMapper;
    private final LoggingService loggingService;

    @Override
    public List<LinkResponse> getLinks(UUID organizationId, LinkQueryRequest request) {
        UUID versionId = request.getVersionId();
        UUID repositoryId = request.getRepositoryId();
        if (versionId == null && repositoryId == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LinkServiceLogConstant.SCOPE_MISSING, organizationId);
            throw new ValidationException(ExceptionValidationError.VALIDATION,
                    Map.of(LinkConstant.FIELD_VERSION_ID, LinkConstant.SCOPE_REQUIRED_MESSAGE));
        }
        if (repositoryId == null) {
            return getVersionLinks(organizationId, versionId);
        }
        if (versionId == null) {
            return getReleaseLinks(organizationId, repositoryId);
        }
        return getComposedLinks(organizationId, repositoryId, versionId);
    }

    @Override
    public DependencyResponse getDependencies(UUID organizationId, Collection<UUID> versionIds, String path) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LinkServiceLogConstant.READING_DEPENDENCIES,
                path, versionIds.size(), organizationId);
        List<Version> versions = versionQueryService.getEntitiesByIds(organizationId, versionIds);
        NavigableMap<String, ScopeFile> scope = new TreeMap<>();
        for (Version version : versions) {
            versionQueryService.render(organizationId, version)
                    .forEach(file -> scope.putIfAbsent(file.path(), new ScopeFile(version.getId(), file)));
        }
        ScopeFile self = scope.get(path);
        if (self == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LinkServiceLogConstant.DEPENDENCY_PATH_NOT_FOUND,
                    versions.size(), organizationId, path);
            throw new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(LinkConstant.FIELD_PATH, path));
        }
        List<UUID> scopeVersionIds = versions.stream().map(Version::getId).toList();
        Set<DependencyEmbeddedResponse> linksTo = new HashSet<>();
        Set<DependencyEmbeddedResponse> usedBy = new HashSet<>();
        addMarkdownLinks(path, self, scope, linksTo, usedBy);

        // Pack files are stored at repository paths, so no folder is needed to classify them.
        Optional<DeliveryPath> delivery = DeliveryPaths.classify(path, null);
        if (delivery.isPresent()) {
            DeliveryPath placed = delivery.get();
            switch (placed.kind()) {
                case RULE -> addRuleConventions(organizationId, self, placed, scope, linksTo);
                case CONVENTION -> {
                    addCitingRules(organizationId, scopeVersionIds, path, usedBy);
                    conventionQueryService.getEntitiesByVersionId(organizationId, self.versionId()).stream()
                            .filter(convention -> placed.relativePath().equals(convention.getRelativePath()))
                            .findFirst()
                            .ifPresent(convention -> addSharingSkills(organizationId, SkillPartKind.CONVENTION,
                                    convention.getId(), usedBy));
                }
                case WORKFLOW -> {
                    workflowQueryService.getEntitiesByVersionId(organizationId, self.versionId()).stream()
                            .filter(workflow -> placed.relativePath().equals(workflow.getRelativePath()))
                            .findFirst()
                            .ifPresent(workflow -> addSharingSkills(organizationId, SkillPartKind.WORKFLOW,
                                    workflow.getId(), usedBy));
                }
                case SKILL -> addSkillParts(organizationId, self, placed, scope, linksTo, usedBy);
                default -> {
                    // The other kinds store no relation to another file; their Markdown links are read above.
                }
            }
        }
        List<DependencyEmbeddedResponse> sortedLinksTo = linksTo.stream().sorted(DEPENDENCY_ORDER).toList();
        List<DependencyEmbeddedResponse> sortedUsedBy = usedBy.stream().sorted(DEPENDENCY_ORDER).toList();
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LinkServiceLogConstant.DEPENDENCIES_READ,
                sortedLinksTo.size(), sortedUsedBy.size(), path, organizationId);
        return linkMapper.toDependencyResponse(path, self.file().entityKind(), sortedLinksTo, sortedUsedBy);
    }

    /**
     * The Markdown links of the file, and the Markdown files of the scope that link to it. A
     * directory link does not count as a use of each file below it.
     */
    private void addMarkdownLinks(String path, ScopeFile self, NavigableMap<String, ScopeFile> scope,
                                  Set<DependencyEmbeddedResponse> linksTo, Set<DependencyEmbeddedResponse> usedBy) {
        Map<String, byte[]> sources = new TreeMap<>();
        scope.forEach((sourcePath, source) -> {
            RenderedFile file = source.file();
            if (isLinkSource(file.path(), file.mode(), file.sha256()) && file.content() != null) {
                sources.put(sourcePath, file.content());
            }
        });
        for (LinkCandidate link : LinkExtractor.extractAll(sources)) {
            if (link.sourcePath().equals(path)) {
                boolean delivered = LinkExtractor.isDelivered(link.targetPath(), scope.navigableKeySet());
                if (LinkExtractor.isListed(link.targetPath(), delivered)) {
                    linksTo.add(target(link.targetPath(), DependencyRelation.MARKDOWN_LINK, scope));
                }
            } else if (link.targetPath().equals(path)) {
                usedBy.add(user(scope.get(link.sourcePath()), DependencyRelation.MARKDOWN_LINK));
            }
        }
    }

    private void addRuleConventions(UUID organizationId, ScopeFile self, DeliveryPath placed,
                                    NavigableMap<String, ScopeFile> scope, Set<DependencyEmbeddedResponse> linksTo) {
        ruleQueryService.getEntitiesByVersionId(organizationId, self.versionId()).stream()
                .filter(rule -> placed.relativePath().equals(rule.getRelativePath()))
                .findFirst()
                .ifPresent(rule -> rule.getConventions().forEach(convention ->
                        linksTo.add(target(convention.getConventionPath(), DependencyRelation.RULE_CONVENTION, scope))));
    }

    private void addCitingRules(UUID organizationId, List<UUID> versionIds, String path,
                                Set<DependencyEmbeddedResponse> usedBy) {
        for (Rule rule : ruleQueryService.getEntitiesCitingConvention(organizationId, versionIds, path)) {
            usedBy.add(linkMapper.toDependency(DeliveryPaths.folderOf(FileKind.RULE) + rule.getRelativePath(), EntityKind.RULE,
                    DependencyRelation.RULE_CONVENTION, LinkStatus.FOUND, rule.getVersionId()));
        }
    }

    /**
     * For SKILL.md, the parts the skill is made of. For any other file of the skill directory, the
     * skills that share that part, the skill itself included, since a save of a shared part
     * changes every one of them.
     */
    private void addSkillParts(UUID organizationId, ScopeFile self, DeliveryPath placed, NavigableMap<String, ScopeFile> scope,
                               Set<DependencyEmbeddedResponse> linksTo, Set<DependencyEmbeddedResponse> usedBy) {
        List<SkillPartLink> parts = skillQueryService.getPartLinks(organizationId, self.versionId(), placed.skillName());
        String directory = DeliveryPaths.skillDirectory(placed.skillName());
        if (placed.relativePath().equals(RenderConstant.SKILL_FILE)) {
            for (SkillPartLink part : parts) {
                String partPath = directory + part.path();
                ScopeFile partFile = scope.get(partPath);
                linksTo.add(linkMapper.toDependency(partPath, EntityKind.valueOf(part.kind().name()), DependencyRelation.SKILL_PART,
                        partFile == null ? LinkStatus.BROKEN : LinkStatus.FOUND, partFile == null ? null : partFile.versionId()));
            }
            return;
        }
        parts.stream()
                .filter(part -> part.path().equals(placed.relativePath()))
                .findFirst()
                .ifPresent(part -> addSharingSkills(organizationId, part.kind(), part.partId(), usedBy));
    }

    private void addSharingSkills(UUID organizationId, SkillPartKind kind, UUID partId, Set<DependencyEmbeddedResponse> usedBy) {
        for (SkillPartLink link : skillQueryService.getLinksToPart(organizationId, kind, partId)) {
            usedBy.add(linkMapper.toDependency(DeliveryPaths.skillDirectory(link.skillName()) + RenderConstant.SKILL_FILE,
                    EntityKind.SKILL, DependencyRelation.SKILL_PART, LinkStatus.FOUND, link.versionId()));
        }
    }

    /** A file the scope may or may not deliver: FOUND with its version when it does, BROKEN otherwise. */
    private DependencyEmbeddedResponse target(String targetPath, DependencyRelation relation,
                                              NavigableMap<String, ScopeFile> scope) {
        ScopeFile file = scope.get(targetPath);
        if (file != null) {
            return linkMapper.toDependency(targetPath, file.file().entityKind(), relation, LinkStatus.FOUND, file.versionId());
        }
        boolean delivered = LinkExtractor.isDelivered(targetPath, scope.navigableKeySet());
        EntityKind kind = DeliveryPaths.classify(targetPath, null).map(placed -> placed.entityKind(FileMode.FILE)).orElse(null);
        return linkMapper.toDependency(targetPath, delivered ? null : kind, relation,
                delivered ? LinkStatus.FOUND : LinkStatus.BROKEN, null);
    }

    private DependencyEmbeddedResponse user(ScopeFile source, DependencyRelation relation) {
        return linkMapper.toDependency(source.file().path(), source.file().entityKind(), relation, LinkStatus.FOUND, source.versionId());
    }

    /**
     * The links within one version's files, with no other pack or project composed: what the
     * version itself delivers.
     */
    private List<LinkResponse> getVersionLinks(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LinkServiceLogConstant.READING_VERSION_LINKS, versionId, organizationId);
        Version version = versionQueryService.getEntityById(organizationId, versionId);
        List<RenderedFile> files = versionQueryService.render(organizationId, version);
        Map<String, byte[]> contentsByPath = new TreeMap<>();
        files.stream()
                .filter(file -> isLinkSource(file.path(), file.mode(), file.sha256()) && file.content() != null)
                .forEach(file -> contentsByPath.put(file.path(), file.content()));
        return toLinks(contentsByPath, files.stream().map(RenderedFile::path).collect(Collectors.toCollection(TreeSet::new)));
    }

    private List<LinkResponse> getReleaseLinks(UUID organizationId, UUID repositoryId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LinkServiceLogConstant.READING_RELEASE_LINKS, repositoryId, organizationId);
        Optional<Release> latest = releaseQueryService.findLatest(organizationId, repositoryId);
        if (latest.isEmpty()) {
            return List.of();
        }
        List<ReleaseEntry> entries = latest.get().getManifest();
        Map<String, String> sha256ByPath = new TreeMap<>();
        entries.stream()
                .filter(entry -> isLinkSource(entry.path(), entry.mode(), entry.sha256()))
                .forEach(entry -> sha256ByPath.put(entry.path(), entry.sha256()));
        Map<String, byte[]> contents = blobQueryService.getContents(organizationId, Set.copyOf(sha256ByPath.values()));
        Map<String, byte[]> contentsByPath = new TreeMap<>();
        sha256ByPath.forEach((path, sha256) -> {
            byte[] content = contents.get(sha256);
            if (content != null) {
                contentsByPath.put(path, content);
            }
        });
        return toLinks(contentsByPath, entries.stream().map(ReleaseEntry::path).collect(Collectors.toCollection(TreeSet::new)));
    }

    /**
     * Composes the repository with the version in place of its owner's current one, so the links
     * show what approving it would deliver, the files of the other packs and projects included.
     */
    private List<LinkResponse> getComposedLinks(UUID organizationId, UUID repositoryId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LinkServiceLogConstant.READING_COMPOSED_LINKS,
                repositoryId, organizationId, versionId);
        // Resolving the repository first gives the 404 for one of another organization.
        releaseQueryService.findLatest(organizationId, repositoryId);
        Version version = versionQueryService.getEntityById(organizationId, versionId);
        if (!OPEN_STATUSES.contains(version.getStatus())) {
            refuse(organizationId, repositoryId, versionId, String.format(LinkConstant.VERSION_NOT_OPEN_MESSAGE, version.getStatus()));
        }
        if (!releaseQueryService.getComposingRepositoryIds(organizationId, version).contains(repositoryId)) {
            refuse(organizationId, repositoryId, versionId, LinkConstant.VERSION_NOT_COMPOSED_MESSAGE);
        }
        Composition composition = releaseQueryService.previewComposition(organizationId, repositoryId,
                Map.of(version.getPackId(), version.getId()));
        Map<String, byte[]> contentsByPath = new TreeMap<>();
        composition.files().stream()
                .filter(file -> isLinkSource(file.path(), file.mode(), file.sha256()))
                .forEach(file -> {
                    byte[] content = composition.contents().get(file.sha256());
                    if (content != null) {
                        contentsByPath.put(file.path(), content);
                    }
                });
        return toLinks(contentsByPath, composition.files().stream().map(ComposedFile::path)
                .collect(Collectors.toCollection(TreeSet::new)));
    }

    private void refuse(UUID organizationId, UUID repositoryId, UUID versionId, String reason) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, LinkServiceLogConstant.VERSION_REFUSED,
                repositoryId, organizationId, versionId, reason);
        throw new ValidationException(ExceptionValidationError.VALIDATION, Map.of(LinkConstant.FIELD_VERSION_ID, reason));
    }

    private List<LinkResponse> toLinks(Map<String, byte[]> contentsByPath, NavigableSet<String> deliveredPaths) {
        if (contentsByPath.isEmpty()) {
            return List.of();
        }
        List<LinkResponse> links = new ArrayList<>();
        for (LinkCandidate link : LinkExtractor.extractAll(contentsByPath)) {
            boolean delivered = LinkExtractor.isDelivered(link.targetPath(), deliveredPaths);
            LinkStatus status = delivered ? LinkStatus.FOUND : LinkStatus.BROKEN;
            if (LinkExtractor.isListed(link.targetPath(), delivered)) {
                links.add(linkMapper.toResponse(link, status));
            }
        }
        return links;
    }

    private static boolean isLinkSource(String path, FileMode mode, String sha256) {
        return mode != FileMode.SYMLINK && sha256 != null && path.endsWith(ValidationConstant.MARKDOWN_SUFFIX);
    }
}
