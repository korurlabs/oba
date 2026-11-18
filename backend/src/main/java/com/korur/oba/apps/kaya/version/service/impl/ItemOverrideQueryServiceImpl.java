package com.korur.oba.apps.kaya.version.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.service.RenderQueryService;
import com.korur.oba.apps.kaya.validation.helper.PathRules;
import com.korur.oba.apps.kaya.version.constant.VersionConstant;
import com.korur.oba.apps.kaya.version.dto.embedded.ItemSource;
import com.korur.oba.apps.kaya.version.entity.ItemOverride;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;
import com.korur.oba.apps.kaya.version.repository.ItemOverrideRepository;
import com.korur.oba.apps.kaya.version.repository.VersionRepository;
import com.korur.oba.apps.kaya.version.service.ItemOverrideQueryService;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;

/**
 * Implements {@link ItemOverrideQueryService} over the override rows and the rendered files of the
 * versions they are measured against.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemOverrideQueryServiceImpl implements ItemOverrideQueryService {

    private static final List<VersionStatus> RELEASED = List.of(VersionStatus.APPROVED, VersionStatus.SUPERSEDED);

    private final ItemOverrideRepository itemOverrideRepository;
    private final VersionRepository versionRepository;
    private final VersionQueryService versionQueryService;
    private final PackQueryService packQueryService;
    private final RenderQueryService renderQueryService;

    /** The state that makes two files of an item the same. */
    private record FileState(FileMode mode, String sha256, String symlinkTarget) {

        static FileState of(RenderedFile file) {
            return new FileState(file.mode(), file.sha256(), file.symlinkTarget());
        }
    }

    @Override
    public List<ItemOverride> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        return itemOverrideRepository.findByOrganizationIdAndVersionIdOrderByPathAsc(organizationId, versionId);
    }

    @Override
    public List<ItemOverride> getEntitiesByVersionIds(UUID organizationId, Collection<UUID> versionIds) {
        if (versionIds.isEmpty()) {
            return List.of();
        }
        return itemOverrideRepository.findByOrganizationIdAndVersionIdIn(organizationId, versionIds);
    }

    @Override
    public Map<String, ItemSource> getInheritedSources(UUID organizationId, UUID packId) {
        List<Pack> chain = packQueryService.getChain(organizationId, packId);
        Map<String, ItemSource> sources = new TreeMap<>();
        for (Pack ancestor : chain.subList(0, chain.size() - 1)) {
            if (ancestor.getCurrentVersionId() == null) {
                continue;
            }
            Version current = versionQueryService.getEntityById(organizationId, ancestor.getCurrentVersionId());
            ItemSource source = new ItemSource(ancestor.getId(), current.getId());
            for (RenderedFile file : versionQueryService.render(organizationId, current)) {
                sources.put(PathRules.itemOf(file.path()), source);
            }
        }
        return sources;
    }

    @Override
    public boolean isItemUnchanged(UUID organizationId, UUID fromVersionId, UUID toVersionId, String item) {
        if (Objects.equals(fromVersionId, toVersionId)) {
            return true;
        }
        return statesOf(renderItem(organizationId, fromVersionId, item))
                .equals(statesOf(renderItem(organizationId, toVersionId, item)));
    }

    @Override
    public int getItemNumber(UUID organizationId, UUID versionId, String item) {
        Version version = versionQueryService.getEntityById(organizationId, versionId);
        List<Version> released = versionRepository.findByPackIdAndStatusInAndNumberLessThanEqualOrderByNumberAsc(
                version.getPackId(), RELEASED, version.getNumber());
        Map<UUID, Map<String, FileState>> states = itemStates(organizationId, released, item);
        Map<String, FileState> previous = Map.of();
        int number = 0;
        for (Version each : released) {
            Map<String, FileState> state = states.getOrDefault(each.getId(), Map.of());
            if (!state.equals(previous)) {
                number++;
            }
            previous = state;
        }
        return number;
    }

    @Override
    public List<RenderedFile> filesOf(List<RenderedFile> files, String item) {
        return files.stream().filter(file -> PathRules.itemOf(file.path()).equals(item)).toList();
    }

    /**
     * The item's files in each version. A file item is read in one path render over every version;
     * a skill directory needs each version's files.
     */
    private Map<UUID, Map<String, FileState>> itemStates(UUID organizationId, List<Version> versions, String item) {
        Map<UUID, Map<String, FileState>> states = new HashMap<>();
        if (versions.isEmpty()) {
            return states;
        }
        if (!item.endsWith(VersionConstant.ITEM_DIRECTORY_SUFFIX)) {
            List<UUID> ids = versions.stream().map(Version::getId).toList();
            renderQueryService.renderPath(organizationId, ids, item)
                    .forEach((id, file) -> states.put(id, Map.of(file.path(), FileState.of(file))));
            return states;
        }
        for (Version version : versions) {
            states.put(version.getId(), statesOf(filesOf(versionQueryService.render(organizationId, version), item)));
        }
        return states;
    }

    private List<RenderedFile> renderItem(UUID organizationId, UUID versionId, String item) {
        if (versionId == null) {
            return List.of();
        }
        Version version = versionQueryService.getEntityById(organizationId, versionId);
        return filesOf(versionQueryService.render(organizationId, version), item);
    }

    private static Map<String, FileState> statesOf(List<RenderedFile> files) {
        Map<String, FileState> states = new TreeMap<>();
        for (RenderedFile file : files) {
            states.put(file.path(), FileState.of(file));
        }
        return states;
    }
}
