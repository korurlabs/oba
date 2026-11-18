package com.korur.oba.apps.kaya.release.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.release.constant.ReleaseConstant;
import com.korur.oba.apps.kaya.release.constant.service.ReleaseServiceLogConstant;
import com.korur.oba.apps.kaya.release.dto.request.ReleaseQueryRequest;
import com.korur.oba.apps.kaya.release.dto.response.EffectiveItemResponse;
import com.korur.oba.apps.kaya.release.dto.response.EffectiveOverrideResponse;
import com.korur.oba.apps.kaya.release.dto.response.ReleaseDetailResponse;
import com.korur.oba.apps.kaya.release.dto.response.ReleaseResponse;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.enums.ItemLock;
import com.korur.oba.apps.kaya.release.enums.OverrideStatus;
import com.korur.oba.apps.kaya.release.helper.Composer;
import com.korur.oba.apps.kaya.release.helper.Composition;
import com.korur.oba.apps.kaya.release.mapper.ReleaseMapper;
import com.korur.oba.apps.kaya.release.repository.ReleaseRepository;
import com.korur.oba.apps.kaya.release.repository.RepositoryPackRepository;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.signing.dto.embedded.SignedManifest;
import com.korur.oba.apps.kaya.signing.helper.SignedManifestWriter;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.helper.MountCandidate;
import com.korur.oba.apps.kaya.validation.helper.PathRules;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.entity.ItemOverride;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.service.ItemOverrideQueryService;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;

/**
 * Implements {@link ReleaseQueryService} over the release and repository-pack tables; previews and
 * checks compose through {@link com.korur.oba.apps.kaya.release.helper.Composer} and write nothing.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseQueryServiceImpl implements ReleaseQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ReleaseQueryServiceImpl.class);

    private final ReleaseRepository releaseRepository;
    private final RepositoryPackRepository repositoryPackRepository;
    private final Composer composer;
    private final RepositoryQueryService repositoryQueryService;
    private final PackQueryService packQueryService;
    private final ValidationQueryService validationQueryService;
    private final VersionQueryService versionQueryService;
    private final ItemOverrideQueryService itemOverrideQueryService;
    private final ReleaseMapper releaseMapper;
    private final LoggingService loggingService;

    @Override
    public Page<ReleaseResponse> getReleases(UUID organizationId, UUID repositoryId, ReleaseQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.LISTING_RELEASES,
                repositoryId, organizationId, request);
        repositoryQueryService.getEntityById(organizationId, repositoryId);
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(),
                Sort.by(Sort.Direction.DESC, ReleaseConstant.FIELD_NUMBER));
        return releaseRepository.findByRepositoryId(repositoryId, pageable).map(releaseMapper::toResponse);
    }

    @Override
    public ReleaseDetailResponse getRelease(UUID organizationId, UUID repositoryId, int number) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.READING_RELEASE,
                number, repositoryId, organizationId);
        return releaseMapper.toDetailResponse(getEntityByNumber(organizationId, repositoryId, number));
    }

    @Override
    public Optional<Release> findLatest(UUID organizationId, UUID repositoryId) {
        repositoryQueryService.getEntityById(organizationId, repositoryId);
        return releaseRepository.findFirstByRepositoryIdOrderByNumberDesc(repositoryId);
    }

    @Override
    public Release getEntityByNumber(UUID organizationId, UUID repositoryId, int number) {
        repositoryQueryService.getEntityById(organizationId, repositoryId);
        return releaseRepository.findByRepositoryIdAndNumber(repositoryId, number).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.RELEASE_NOT_FOUND,
                    number, repositoryId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(ReleaseConstant.FIELD_REPOSITORY_ID, repositoryId.toString(), ReleaseConstant.FIELD_RELEASE, number));
        });
    }

    @Override
    public Optional<Release> findByNumber(UUID organizationId, UUID repositoryId, int number) {
        repositoryQueryService.getEntityById(organizationId, repositoryId);
        return releaseRepository.findByRepositoryIdAndNumber(repositoryId, number);
    }

    @Override
    public Release getEntityById(UUID organizationId, UUID releaseId) {
        Optional<Release> release = releaseRepository.findById(releaseId);
        if (release.isEmpty() || !repositoryIdsOf(organizationId).contains(release.get().getRepositoryId())) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.RELEASE_ID_NOT_FOUND,
                    releaseId, organizationId);
            throw new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(ReleaseConstant.FIELD_RELEASE_ID, releaseId.toString()));
        }
        return release.get();
    }

    @Override
    public boolean isDelivered(UUID organizationId, String sha256) {
        List<UUID> repositoryIds = repositoryIdsOf(organizationId);
        return !repositoryIds.isEmpty() && releaseRepository.existsDeliveringSha256(repositoryIds, sha256);
    }

    @Override
    public String toCanonicalJson(UUID organizationId, Release release) {
        Repository repository = repositoryQueryService.getEntityById(organizationId, release.getRepositoryId());
        SignedManifest manifest = releaseMapper.toSignedManifest(organizationId.toString(), repository.getRemoteUrl(),
                release.getNumber(), SignedManifestWriter.formatCreatedAt(release.getCreatedAt()), release.getManifest());
        return SignedManifestWriter.toCanonicalJson(manifest);
    }

    @Override
    public Composition previewComposition(UUID organizationId, UUID repositoryId, Map<UUID, UUID> versionIdsByOwner) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.COMPOSING,
                repositoryId, organizationId, versionIdsByOwner.size());
        return composer.compose(organizationId, repositoryId, versionIdsByOwner);
    }

    @Override
    public List<UUID> getComposingRepositoryIds(UUID organizationId, Version version) {
        Set<UUID> reaching = new LinkedHashSet<>();
        reaching.add(packQueryService.getEntityById(organizationId, version.getPackId()).getId());
        reaching.addAll(packQueryService.getDescendantIds(organizationId, version.getPackId()));
        boolean required = packQueryService.getEntitiesByIds(organizationId, reaching).stream()
                .anyMatch(pack -> Boolean.TRUE.equals(pack.getRequired()));
        if (required) {
            return getActiveRepositoryIds(organizationId);
        }
        return repositoryPackRepository.findRepositoryIdsByPackIdIn(reaching);
    }

    @Override
    public List<Violation> validateVersion(UUID organizationId, Version version) {
        Map<UUID, UUID> replaced = Map.of(version.getPackId(), version.getId());
        List<Violation> violations = new ArrayList<>();
        for (UUID repositoryId : getComposingRepositoryIds(organizationId, version)) {
            Composition composition = composer.compose(organizationId, repositoryId, replaced);
            violations.addAll(validationQueryService.validateComposition(composition.toCandidate()));
        }
        violations.addAll(getLockViolations(organizationId, version.getPackId(),
                versionQueryService.render(organizationId, version).stream().map(RenderedFile::path).toList()));
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.VERSION_CHECKED,
                version.getId(), organizationId, violations.size());
        return violations;
    }

    @Override
    public List<Violation> getLockViolations(UUID organizationId, UUID packId, Collection<String> paths) {
        List<Pack> chain = packQueryService.getChain(organizationId, packId);
        Set<String> items = paths.stream().map(PathRules::itemOf).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Violation> violations = new ArrayList<>();
        for (Pack ancestor : chain.subList(0, chain.size() - 1)) {
            boolean required = Boolean.TRUE.equals(ancestor.getRequired());
            Set<String> given = currentItems(organizationId, ancestor);
            for (String item : items) {
                if (given.contains(item) && PathRules.isLocked(item, required)) {
                    violations.add(Composer.lockViolation(null, item, ancestor, required, packId));
                }
            }
        }
        if (!violations.isEmpty()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.ITEMS_LOCKED,
                    packId, organizationId, violations.size());
        }
        return violations;
    }

    @Override
    public List<Violation> getRuleScopeViolations(UUID organizationId, UUID packId, String path, FileMode mode, byte[] content) {
        Pack pack = packQueryService.getEntityById(organizationId, packId);
        List<Violation> violations = new ArrayList<>();
        for (String folder : repositoryPackRepository.findRootPathsByPackId(packId)) {
            Composer.ruleScopeViolation(null, new MountCandidate(packId, pack.getName(), folder), path, mode, content)
                    .ifPresent(violations::add);
        }
        if (!violations.isEmpty()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.MOUNT_RULE_UNSCOPED,
                    path, packId, organizationId, violations.size());
        }
        return violations;
    }

    @Override
    public List<Violation> getLockedOverrides(UUID organizationId, UUID packId) {
        Pack pack = packQueryService.getEntityById(organizationId, packId);
        Set<String> given = currentItems(organizationId, pack);
        List<Violation> violations = new ArrayList<>();
        if (given.isEmpty()) {
            return violations;
        }
        List<UUID> descendantIds = packQueryService.getDescendantIds(organizationId, packId);
        // Derived from the items themselves, not from the recorded overrides: a record is written only
        // when a descendant saves its version, so a path the pack gained after a descendant already
        // held it has no record and would otherwise let the pack become required over it.
        List<Pack> descendants = packQueryService.getEntitiesByIds(organizationId, descendantIds).stream()
                .sorted(Comparator.comparing((Pack descendant) -> descendantIds.indexOf(descendant.getId())))
                .toList();
        for (Pack descendant : descendants) {
            for (String item : new TreeSet<>(currentItems(organizationId, descendant))) {
                if (given.contains(item)) {
                    violations.add(Composer.lockViolation(null, item, pack, true, descendant.getId()));
                }
            }
        }
        return violations;
    }

    @Override
    public List<EffectiveItemResponse> getEffective(UUID organizationId, UUID packId, UUID versionId, boolean inherited) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.READING_EFFECTIVE,
                packId, versionId, organizationId, inherited);
        Pack pack = packQueryService.getEntityById(organizationId, packId);
        Version version = ownVersion(organizationId, pack, versionId);
        List<Pack> chain = packQueryService.getChain(organizationId, packId);

        // The ancestors root first, so a nearer ancestor replaces a farther one per item.
        Map<String, InheritedItem> nearest = new TreeMap<>();
        Map<String, ItemLock> locks = new HashMap<>();
        for (Pack ancestor : chain.subList(0, chain.size() - 1)) {
            if (ancestor.getCurrentVersionId() == null) {
                continue;
            }
            Version current = versionQueryService.getEntityById(organizationId, ancestor.getCurrentVersionId());
            boolean required = Boolean.TRUE.equals(ancestor.getRequired());
            itemsOf(versionQueryService.render(organizationId, current)).forEach((item, files) -> {
                nearest.put(item, new InheritedItem(ancestor.getId(), current.getId(), kindOf(item, files)));
                if (PathRules.isLocked(item, required)) {
                    locks.merge(item, required ? ItemLock.REQUIRED : ItemLock.GUARDRAIL,
                            (first, second) -> first == ItemLock.REQUIRED ? first : second);
                }
            });
        }

        Map<String, List<RenderedFile>> own = itemsOf(versionQueryService.render(organizationId, version));
        Map<String, ItemOverride> recorded = new HashMap<>();
        if (version != null) {
            itemOverrideQueryService.getEntitiesByVersionId(organizationId, version.getId())
                    .forEach(override -> recorded.put(override.getPath(), override));
        }
        List<EffectiveItemResponse> items = new ArrayList<>();
        own.forEach((item, files) -> items.add(releaseMapper.toEffectiveItem(item, kindOf(item, files), pack.getId(),
                version.getId(), true, locks.get(item),
                overrideOf(organizationId, item, nearest.get(item), recorded.get(item)))));
        if (inherited) {
            nearest.forEach((item, source) -> {
                if (!own.containsKey(item)) {
                    items.add(releaseMapper.toEffectiveItem(item, source.kind(), source.packId(), source.versionId(), false,
                            locks.get(item), null));
                }
            });
        }
        items.sort(Comparator.comparing(EffectiveItemResponse::path));
        return items;
    }

    /** An item an ancestor's current version gives, with what it is stored as. */
    private record InheritedItem(UUID packId, UUID versionId, EntityKind kind) {
    }

    /**
     * The version of the pack the effective view reads: the named one, which must belong to the
     * pack, else the pack's current version; null for a pack without versions.
     */
    private Version ownVersion(UUID organizationId, Pack pack, UUID versionId) {
        if (versionId == null) {
            return pack.getCurrentVersionId() == null
                    ? null
                    : versionQueryService.getEntityById(organizationId, pack.getCurrentVersionId());
        }
        Version version = versionQueryService.getEntityById(organizationId, versionId);
        if (!pack.getId().equals(version.getPackId())) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.VERSION_NOT_OF_PACK,
                    versionId, pack.getId(), organizationId);
            throw new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(ReleaseConstant.FIELD_VERSION_ID, versionId.toString()));
        }
        return version;
    }

    /**
     * The override an own item makes: none when no ancestor gives the item and no record exists;
     * PARENT_REMOVED when only the record is left; otherwise CURRENT exactly when the nearest
     * ancestor's current item equals the item at the recorded base.
     */
    private EffectiveOverrideResponse overrideOf(UUID organizationId, String item, InheritedItem parent,
                                                 ItemOverride recorded) {
        if (parent == null && recorded == null) {
            return null;
        }
        UUID baseVersionId = recorded == null ? null : recorded.getBaseVersionId();
        UUID basePackId = recorded == null ? parent.packId() : recorded.getBasePackId();
        Integer baseItemVersion = baseVersionId == null
                ? null
                : itemOverrideQueryService.getItemNumber(organizationId, baseVersionId, item);
        if (parent == null) {
            return releaseMapper.toEffectiveOverride(basePackId, baseVersionId, baseItemVersion, null, null,
                    OverrideStatus.PARENT_REMOVED);
        }
        Integer parentItemVersion = itemOverrideQueryService.getItemNumber(organizationId, parent.versionId(), item);
        OverrideStatus status = baseVersionId != null
                && itemOverrideQueryService.isItemUnchanged(organizationId, baseVersionId, parent.versionId(), item)
                ? OverrideStatus.CURRENT
                : OverrideStatus.PARENT_UPDATED;
        return releaseMapper.toEffectiveOverride(basePackId, baseVersionId, baseItemVersion, parent.versionId(),
                parentItemVersion, status);
    }

    /** The files of a version by item, items sorted. */
    private static Map<String, List<RenderedFile>> itemsOf(List<RenderedFile> files) {
        Map<String, List<RenderedFile>> items = new TreeMap<>();
        for (RenderedFile file : files) {
            items.computeIfAbsent(PathRules.itemOf(file.path()), item -> new ArrayList<>()).add(file);
        }
        return items;
    }

    /** A skill directory is a skill; any other item is what its one file is stored as. */
    private static EntityKind kindOf(String item, List<RenderedFile> files) {
        if (!item.equals(files.get(0).path())) {
            return EntityKind.SKILL;
        }
        return files.get(0).entityKind();
    }

    /** The items of a pack's current version, as {@link PathRules#itemOf} gives them; empty without one. */
    private Set<String> currentItems(UUID organizationId, Pack pack) {
        if (pack.getCurrentVersionId() == null) {
            return Set.of();
        }
        Version current = versionQueryService.getEntityById(organizationId, pack.getCurrentVersionId());
        return versionQueryService.render(organizationId, current).stream()
                .map(file -> PathRules.itemOf(file.path()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public List<UUID> getActiveRepositoryIds(UUID organizationId) {
        return repositoryQueryService.getRepositories(organizationId).stream()
                .filter(repository -> repository.status() == RepositoryStatus.ACTIVE)
                .map(RepositoryResponse::id)
                // The order PostgreSQL gives uuids, which the text form keeps and UUID.compareTo
                // does not: release locks are taken in this order wherever several are taken, so
                // two callers never wait on each other.
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }

    private List<UUID> repositoryIdsOf(UUID organizationId) {
        return repositoryQueryService.getRepositories(organizationId).stream().map(RepositoryResponse::id).toList();
    }
}
