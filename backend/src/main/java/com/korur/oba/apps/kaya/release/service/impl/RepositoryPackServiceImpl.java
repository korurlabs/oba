package com.korur.oba.apps.kaya.release.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.pack.service.PackService;
import com.korur.oba.apps.kaya.release.constant.ReleaseConstant;
import com.korur.oba.apps.kaya.release.constant.service.ReleaseServiceLogConstant;
import com.korur.oba.apps.kaya.release.dto.embedded.PackVersionEntry;
import com.korur.oba.apps.kaya.release.dto.request.PackRequiredRequest;
import com.korur.oba.apps.kaya.release.dto.request.RepositoryPackUpdateRequest;
import com.korur.oba.apps.kaya.release.dto.response.PackRequiredResponse;
import com.korur.oba.apps.kaya.release.dto.response.RepositoryPackResponse;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.entity.RepositoryPack;
import com.korur.oba.apps.kaya.release.helper.Composer;
import com.korur.oba.apps.kaya.release.helper.Composition;
import com.korur.oba.apps.kaya.release.helper.PackMount;
import com.korur.oba.apps.kaya.release.mapper.ReleaseMapper;
import com.korur.oba.apps.kaya.release.repository.ReleaseRepository;
import com.korur.oba.apps.kaya.release.repository.RepositoryPackRepository;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.release.service.ReleaseService;
import com.korur.oba.apps.kaya.release.service.RepositoryPackQueryService;
import com.korur.oba.apps.kaya.release.service.RepositoryPackService;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.entity.Version;

/**
 * Implements {@link RepositoryPackService}: replaces a repository's pack rows under its release lock
 * and releases the repository when the composed order changed, and sets a pack's required flag
 * under the release locks of every active repository, releasing each whose composition changed.
 */
@Service
@RequiredArgsConstructor
public class RepositoryPackServiceImpl implements RepositoryPackService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RepositoryPackServiceImpl.class);
    private static final Pattern ROOT_PATH = Pattern.compile(ReleaseConstant.ROOT_PATH_PATTERN);

    private final RepositoryPackRepository repositoryPackRepository;
    private final ReleaseRepository releaseRepository;
    private final Composer composer;
    private final ReleaseService releaseService;
    private final ReleaseQueryService releaseQueryService;
    private final RepositoryPackQueryService repositoryPackQueryService;
    private final ValidationQueryService validationQueryService;
    private final RepositoryQueryService repositoryQueryService;
    private final PackQueryService packQueryService;
    private final PackService packService;
    private final ReleaseMapper releaseMapper;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public RepositoryPackResponse replacePacks(User actor, UUID organizationId, UUID repositoryId,
                                               RepositoryPackUpdateRequest request) {
        List<PackMount> mounts = request.packs().stream()
                .map(item -> new PackMount(item.packId(), item.rootPath()))
                .toList();
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.REPLACING_PACKS,
                actor.getId(), repositoryId, organizationId, mounts);
        repositoryQueryService.getEntityById(organizationId, repositoryId);
        requireValidFolders(organizationId, repositoryId, mounts);
        List<UUID> packIds = mounts.stream().map(PackMount::packId).toList();
        // The packs this change adds or removes are locked first, in id order, with the lock an
        // approval of their versions takes before it reads the repositories that compose them: the
        // approval then sees this repository among the pack's either before or after the change,
        // never half. Packs are read only under these locks, so their current versions are fresh.
        Set<UUID> touched = new TreeSet<>(packIds);
        touched.addAll(currentPackIds(repositoryId));
        touched.forEach(releaseService::lockOwner);
        // Taken before the current composition is read, so two pack changes of one repository apply
        // one after the other and neither releases a composition the other replaced.
        releaseService.lockReleases(repositoryId);

        resolvePacks(organizationId, packIds);
        if (currentMounts(repositoryId).equals(mounts)) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.PACKS_UNCHANGED, repositoryId, organizationId);
            return releaseMapper.toPackResponse(repositoryId, repositoryPackQueryService.getComposedPacks(organizationId, mounts),
                    latestRelease(repositoryId));
        }

        Composition composition = composer.compose(organizationId, repositoryId, mounts, Map.of());
        validationQueryService.requireValid(validationQueryService.validateComposition(composition.toCandidate()));

        List<RepositoryPack> rows = writeRows(repositoryId, mounts);

        Optional<Release> release = causeOf(composition)
                .flatMap(causeVersionId -> releaseService.releaseRepository(organizationId, repositoryId, causeVersionId));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.PACKS_REPLACED,
                repositoryId, organizationId, rows.size());
        return releaseMapper.toPackResponse(repositoryId, repositoryPackQueryService.getComposedPacks(organizationId, mounts),
                release.orElseGet(() -> latestRelease(repositoryId)));
    }

    @Override
    @Transactional
    public boolean writePacks(UUID organizationId, UUID repositoryId, List<PackMount> mounts) {
        requireValidFolders(organizationId, repositoryId, mounts);
        List<UUID> packIds = mounts.stream().map(PackMount::packId).toList();
        Set<UUID> touched = new TreeSet<>(packIds);
        touched.addAll(currentPackIds(repositoryId));
        touched.forEach(releaseService::lockOwner);
        releaseService.lockReleases(repositoryId);
        resolvePacks(organizationId, packIds);
        if (currentMounts(repositoryId).equals(mounts)) {
            return false;
        }
        List<RepositoryPack> rows = writeRows(repositoryId, mounts);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.PACKS_REPLACED,
                repositoryId, organizationId, rows.size());
        return true;
    }

    @Override
    @Transactional
    public PackRequiredResponse setPackRequired(User actor, UUID organizationId, UUID packId, PackRequiredRequest request) {
        boolean required = request.required();
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.SETTING_PACK_REQUIRED,
                actor.getId(), packId, organizationId, required);
        // The lock an approval of the pack's versions takes, so an approval reads the flag, and the
        // repositories the pack reaches, either before or after this change, never half.
        releaseService.lockOwner(packId);
        Pack pack = packQueryService.getEntityById(organizationId, packId);
        if (Boolean.valueOf(required).equals(pack.getRequired())) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.PACK_REQUIRED_UNCHANGED,
                    packId, organizationId, required);
            return releaseMapper.toPackRequiredResponse(packId, required, 0);
        }
        // A required pack locks its items for every descendant, so a descendant that overrides one
        // already would be left with an item it may not hold.
        if (required) {
            List<Violation> overrides = releaseQueryService.getLockedOverrides(organizationId, packId);
            if (!overrides.isEmpty()) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.PACK_REQUIRED_OVERRIDDEN,
                        packId, organizationId, overrides.size());
            }
            validationQueryService.requireValid(overrides);
        }
        // Locked in the order every multi-repository release takes them, and before any composition
        // is read, so no release of another owner slips in between the two compositions below.
        List<UUID> repositoryIds = releaseQueryService.getActiveRepositoryIds(organizationId);
        repositoryIds.forEach(releaseService::lockReleases);

        Map<UUID, Composition> before = new LinkedHashMap<>();
        for (UUID repositoryId : repositoryIds) {
            before.put(repositoryId, composer.compose(organizationId, repositoryId, Map.of()));
        }
        // Written before the compositions after the change are read, which see the flag in this
        // transaction; a refusal below rolls it back with everything else.
        packService.setRequired(organizationId, packId, required);

        List<UUID> changed = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();
        for (UUID repositoryId : repositoryIds) {
            Composition old = before.get(repositoryId);
            Composition next = composer.compose(organizationId, repositoryId, Map.of());
            if (next.files().equals(old.files()) && next.conflicts().equals(old.conflicts())) {
                continue;
            }
            violations.addAll(validationQueryService.validateComposition(next.toCandidate()));
            changed.add(repositoryId);
        }
        validationQueryService.requireValid(violations);

        int releaseCount = 0;
        for (UUID repositoryId : changed) {
            if (releaseService.releaseRepository(organizationId, repositoryId, pack.getCurrentVersionId()).isPresent()) {
                releaseCount++;
            }
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.PACK_REQUIRED_RELEASED,
                packId, organizationId, required, releaseCount, repositoryIds.size());
        return releaseMapper.toPackRequiredResponse(packId, required, releaseCount);
    }

    private List<RepositoryPack> writeRows(UUID repositoryId, List<PackMount> mounts) {
        repositoryPackRepository.deleteByRepositoryId(repositoryId);
        List<RepositoryPack> rows = new ArrayList<>();
        for (int position = 0; position < mounts.size(); position++) {
            PackMount mount = mounts.get(position);
            rows.add(RepositoryPack.builder().repositoryId(repositoryId).packId(mount.packId()).rootPath(mount.rootPath())
                    .position(position).build());
        }
        return repositoryPackRepository.saveAll(rows);
    }

    private List<PackMount> currentMounts(UUID repositoryId) {
        return repositoryPackRepository.findByRepositoryIdOrderByPositionAsc(repositoryId).stream()
                .map(row -> new PackMount(row.getPackId(), row.getRootPath()))
                .toList();
    }

    /**
     * Refuses a mount folder that is malformed, starts with a folder of the delivered config, is
     * given to two packs, or lies inside or around another mount's folder: a session in the inner
     * folder would load both packs' AGENTS.md and rules meant for one folder only.
     */
    private void requireValidFolders(UUID organizationId, UUID repositoryId, List<PackMount> mounts) {
        List<String> folders = new ArrayList<>();
        for (PackMount mount : mounts) {
            String root = mount.rootPath();
            if (root == null) {
                continue;
            }
            if (!ROOT_PATH.matcher(root).matches()) {
                throw mountInvalid(organizationId, repositoryId, root, ReleaseConstant.ROOT_PATH_INVALID_MESSAGE);
            }
            String firstSegment = root.split(ReleaseConstant.PATH_SEPARATOR, 2)[0];
            if (ReleaseConstant.RESERVED_ROOTS.contains(firstSegment)) {
                throw mountInvalid(organizationId, repositoryId, root,
                        String.format(ReleaseConstant.ROOT_PATH_RESERVED_MESSAGE, firstSegment));
            }
            for (String other : folders) {
                if (other.equals(root)) {
                    throw mountInvalid(organizationId, repositoryId, root, String.format(ReleaseConstant.ROOT_PATH_TAKEN_MESSAGE, root));
                }
                if (root.startsWith(other + ReleaseConstant.PATH_SEPARATOR) || other.startsWith(root + ReleaseConstant.PATH_SEPARATOR)) {
                    throw mountInvalid(organizationId, repositoryId, root,
                            String.format(ReleaseConstant.ROOT_PATH_OVERLAP_MESSAGE, root, other));
                }
            }
            folders.add(root);
        }
    }

    private ValidationException mountInvalid(UUID organizationId, UUID repositoryId, String root, String reason) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.MOUNT_REFUSED, root, repositoryId, organizationId,
                reason);
        return new ValidationException(ExceptionDomainKayaError.PACK_MOUNT_INVALID, Map.of(ReleaseConstant.FIELD_ROOT_PATH, reason));
    }

    private List<UUID> currentPackIds(UUID repositoryId) {
        return repositoryPackRepository.findByRepositoryIdOrderByPositionAsc(repositoryId).stream()
                .map(RepositoryPack::getPackId)
                .toList();
    }

    private Release latestRelease(UUID repositoryId) {
        return releaseRepository.findFirstByRepositoryIdOrderByNumberDesc(repositoryId).orElse(null);
    }

    /**
     * Loads the listed packs in request order. An id the organization does not have answers 404,
     * as everywhere else; a pack listed twice is a violation.
     */
    private List<Pack> resolvePacks(UUID organizationId, List<UUID> packIds) {
        Map<UUID, Pack> found = packQueryService.getEntitiesByIds(organizationId, new HashSet<>(packIds)).stream()
                .collect(Collectors.toMap(Pack::getId, Function.identity()));
        List<Violation> violations = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        List<Pack> packs = new ArrayList<>();
        for (UUID packId : packIds) {
            Pack pack = found.get(packId);
            if (pack == null) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.PACK_NOT_FOUND, packId, organizationId);
                throw new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                        Map.of(ReleaseConstant.FIELD_PACK_ID, packId.toString()));
            }
            if (!seen.add(packId)) {
                violations.add(Violation.ofOwner(packId, ViolationRule.PACK_DUPLICATE, ReleaseConstant.PACK_DUPLICATE_MESSAGE));
                continue;
            }
            packs.add(pack);
        }
        validationQueryService.requireValid(violations);
        return packs;
    }

    /**
     * A pack change has no single approved version behind it. The release names the version of the
     * deepest pack at the root, the most specific one the composition rests on, else the first
     * composed version.
     */
    private Optional<UUID> causeOf(Composition composition) {
        Optional<UUID> deepest = composition.packVersions().reversed().stream()
                .filter(entry -> entry.rootPath() == null && entry.versionId() != null)
                .map(PackVersionEntry::versionId)
                .findFirst();
        return deepest.or(() -> composition.versions().stream().findFirst().map(Version::getId));
    }
}
