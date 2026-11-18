package com.korur.oba.apps.kaya.release.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.pack.dto.response.PackDetailResponse;
import com.korur.oba.apps.kaya.pack.dto.response.PackRepositoryResponse;
import com.korur.oba.apps.kaya.pack.dto.response.PackResponse;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.mapper.PackMapper;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.release.constant.service.ReleaseServiceLogConstant;
import com.korur.oba.apps.kaya.release.dto.response.RepositoryPackEntryResponse;
import com.korur.oba.apps.kaya.release.dto.response.RepositoryPackResponse;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.entity.RepositoryPack;
import com.korur.oba.apps.kaya.release.helper.PackMount;
import com.korur.oba.apps.kaya.release.mapper.ReleaseMapper;
import com.korur.oba.apps.kaya.release.repository.ReleaseRepository;
import com.korur.oba.apps.kaya.release.repository.RepositoryPackRepository;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.release.service.RepositoryPackQueryService;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;

/**
 * Implements {@link RepositoryPackQueryService} over the repository-pack table, limited to the
 * organization of the repository.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RepositoryPackQueryServiceImpl implements RepositoryPackQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RepositoryPackQueryServiceImpl.class);

    private final RepositoryPackRepository repositoryPackRepository;
    private final ReleaseRepository releaseRepository;
    private final PackQueryService packQueryService;
    private final ReleaseQueryService releaseQueryService;
    private final RepositoryQueryService repositoryQueryService;
    private final ReleaseMapper releaseMapper;
    private final PackMapper packMapper;
    private final LoggingService loggingService;

    @Override
    public List<PackResponse> getPacksWithMountPaths(UUID organizationId) {
        List<UUID> repositoryIds = repositoryQueryService.getRepositories(organizationId).stream()
                .map(RepositoryResponse::id)
                .toList();
        Map<UUID, Set<String>> folders = new LinkedHashMap<>();
        if (!repositoryIds.isEmpty()) {
            for (RepositoryPack row : repositoryPackRepository.findByRepositoryIdInAndRootPathIsNotNullOrderByRootPathAsc(repositoryIds)) {
                folders.computeIfAbsent(row.getPackId(), packId -> new TreeSet<>()).add(row.getRootPath());
            }
        }
        return packQueryService.getPacks(organizationId).stream()
                .map(pack -> folders.containsKey(pack.id()) ? pack.withMountPaths(new ArrayList<>(folders.get(pack.id()))) : pack)
                .toList();
    }

    @Override
    public PackDetailResponse getPackWithRepositories(UUID organizationId, UUID packId) {
        // Resolving the pack first gives the 404 for one of another organization.
        Pack pack = packQueryService.getEntityById(organizationId, packId);
        // Every active repository composes a required pack whether it lists it or not.
        Set<UUID> composingIds = new HashSet<>(Boolean.TRUE.equals(pack.getRequired())
                ? releaseQueryService.getActiveRepositoryIds(organizationId)
                : repositoryPackRepository.findRepositoryIdsByPackId(packId));
        List<RepositoryResponse> organizationRepositories = repositoryQueryService.getRepositories(organizationId);
        Map<UUID, String> folderByRepository = new HashMap<>();
        if (!organizationRepositories.isEmpty()) {
            List<UUID> organizationRepositoryIds = organizationRepositories.stream().map(RepositoryResponse::id).toList();
            for (RepositoryPack row : repositoryPackRepository.findByRepositoryIdInAndRootPathIsNotNullOrderByRootPathAsc(
                    organizationRepositoryIds)) {
                if (row.getPackId().equals(packId)) {
                    folderByRepository.put(row.getRepositoryId(), row.getRootPath());
                }
            }
        }
        // Filtering the organization's own list, already by name, keeps any other organization's
        // repository out even if a row names one.
        List<PackRepositoryResponse> repositories = organizationRepositories.stream()
                .filter(repository -> composingIds.contains(repository.id()))
                .map(repository -> packMapper.toRepositoryResponse(repository, folderByRepository.get(repository.id()),
                        releaseRepository.findFirstByRepositoryIdOrderByNumberDesc(repository.id())
                                .map(Release::getNumber)
                                .orElse(null)))
                .toList();
        return packMapper.toDetailResponse(pack, new ArrayList<>(new TreeSet<>(folderByRepository.values())), repositories);
    }

    @Override
    public RepositoryPackResponse getPacks(UUID organizationId, UUID repositoryId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReleaseServiceLogConstant.LISTING_PACKS, repositoryId, organizationId);
        List<RepositoryPackEntryResponse> packs = getComposedPacks(organizationId, getMounts(organizationId, repositoryId));
        return releaseMapper.toPackResponse(repositoryId, packs,
                releaseRepository.findFirstByRepositoryIdOrderByNumberDesc(repositoryId).orElse(null));
    }

    @Override
    public List<RepositoryPackEntryResponse> getComposedPacks(UUID organizationId, List<PackMount> mounts) {
        // Required packs first, as they are composed whatever the list says; a required pack the
        // list also names is shown once, with the folder the list gives it.
        Map<UUID, String> composed = new LinkedHashMap<>();
        packQueryService.getRequiredPackIds(organizationId).forEach(packId -> composed.put(packId, null));
        mounts.forEach(mount -> composed.put(mount.packId(), mount.rootPath()));
        Set<UUID> packIds = new LinkedHashSet<>(composed.keySet());
        Map<UUID, Pack> packsById = packQueryService.getEntitiesByIds(organizationId, packIds).stream()
                .collect(Collectors.toMap(Pack::getId, Function.identity()));
        return composed.entrySet().stream()
                .filter(entry -> packsById.containsKey(entry.getKey()))
                .map(entry -> releaseMapper.toPackEntry(packsById.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    @Override
    public List<PackMount> getMounts(UUID organizationId, UUID repositoryId) {
        // Resolving the repository first gives the 404 for one of another organization.
        repositoryQueryService.getEntityById(organizationId, repositoryId);
        return repositoryPackRepository.findByRepositoryIdOrderByPositionAsc(repositoryId).stream()
                .map(row -> new PackMount(row.getPackId(), row.getRootPath()))
                .toList();
    }

    @Override
    public List<UUID> getRepositoryIdsOfPack(UUID organizationId, UUID packId) {
        // Resolving the pack first gives the 404 for one of another organization.
        Pack pack = packQueryService.getEntityById(organizationId, packId);
        // Every active repository composes a required pack whether it lists it or not, so a new
        // version of one reaches them all, as an approval of that version does.
        if (Boolean.TRUE.equals(pack.getRequired())) {
            return releaseQueryService.getActiveRepositoryIds(organizationId);
        }
        return repositoryPackRepository.findRepositoryIdsByPackId(packId);
    }
}
