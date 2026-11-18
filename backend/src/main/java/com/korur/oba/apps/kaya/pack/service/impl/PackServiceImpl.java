package com.korur.oba.apps.kaya.pack.service.impl;

// 1. Framework-level
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.pack.constant.PackConstant;
import com.korur.oba.apps.kaya.pack.constant.service.PackServiceLogConstant;
import com.korur.oba.apps.kaya.pack.dto.request.PackCreateRequest;
import com.korur.oba.apps.kaya.pack.dto.request.PackUpdateRequest;
import com.korur.oba.apps.kaya.pack.dto.response.PackResponse;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.mapper.PackMapper;
import com.korur.oba.apps.kaya.pack.repository.PackRepository;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.pack.service.PackService;
import com.korur.oba.apps.kaya.plan.service.PlanQueryService;
import com.korur.oba.apps.kaya.release.service.RepositoryPackQueryService;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;

/**
 * The {@link PackService} implementation.
 */
@Service
@RequiredArgsConstructor
public class PackServiceImpl implements PackService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PackServiceImpl.class);

    private final PackRepository packRepository;
    private final PackQueryService packQueryService;
    private final RepositoryPackQueryService repositoryPackQueryService;
    private final VersionQueryService versionQueryService;
    private final PlanQueryService planQueryService;
    private final PackMapper packMapper;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public PackResponse createPack(User actor, UUID organizationId, PackCreateRequest request) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.CREATING_PACK,
                actor.getId(), request.name(), request.parentPackId(), organizationId);
        if (request.parentPackId() != null) {
            // Loaded within the organization, so a parent of another organization answers the same
            // 404 as an unknown id.
            packQueryService.getEntityById(organizationId, request.parentPackId());
        }
        if (packRepository.existsByOrganizationIdAndName(organizationId, request.name())) {
            throw nameTaken(organizationId, request.name());
        }

        Pack pack = Pack.builder()
                .organizationId(organizationId)
                .name(request.name())
                .description(request.description())
                .parentPackId(request.parentPackId())
                .build();
        try {
            // Flushed here, so a pack of the same name created after the check above hits the
            // unique constraint now and the caller gets 409 instead of 500 at commit.
            pack = packRepository.saveAndFlush(pack);
        } catch (DataIntegrityViolationException exception) {
            throw nameTaken(organizationId, request.name());
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.PACK_CREATED,
                pack.getId(), pack.getName(), pack.getParentPackId(), organizationId);
        return packMapper.toResponse(pack);
    }

    @Override
    @Transactional
    public PackResponse updatePack(User actor, UUID organizationId, UUID packId, PackUpdateRequest request) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.UPDATING_PACK,
                actor.getId(), packId, request.name(), organizationId);
        Pack pack = packQueryService.getEntityById(organizationId, packId);
        String previousName = pack.getName();
        boolean nameChanged = !previousName.equals(request.name());
        boolean descriptionChanged = !Objects.equals(pack.getDescription(), request.description());
        if (!nameChanged && !descriptionChanged) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.PACK_UPDATE_UNCHANGED,
                    packId, organizationId);
            return withMountPaths(organizationId, pack);
        }
        // Only the name keys anything (a mounted pack's file namespace, seed imports, plan matching),
        // so the in-use and uniqueness checks guard a rename and never a description edit.
        if (nameChanged) {
            Optional<String> condition = inUseCondition(organizationId, pack);
            if (condition.isPresent()) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.PACK_RENAME_REFUSED,
                        packId, organizationId, condition.get());
                throw new ConflictException(ExceptionDomainKayaError.PACK_IN_USE, Map.of(
                        PackConstant.FIELD_PACK_ID, packId.toString(), PackConstant.FIELD_CONDITION, condition.get()));
            }
            if (packRepository.existsByOrganizationIdAndName(organizationId, request.name())) {
                throw nameTaken(organizationId, request.name());
            }
        }

        packMapper.updateEntity(pack, request);
        try {
            // Flushed here, so a pack of the same name created after the check above hits the
            // unique constraint now and the caller gets 409 instead of 500 at commit.
            pack = packRepository.saveAndFlush(pack);
        } catch (DataIntegrityViolationException exception) {
            throw nameTaken(organizationId, request.name());
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.PACK_UPDATED,
                packId, organizationId, previousName, pack.getName(), descriptionChanged);
        return withMountPaths(organizationId, pack);
    }

    @Override
    @Transactional
    public Pack setCurrentVersion(UUID organizationId, UUID packId, UUID versionId) {
        Pack pack = packQueryService.getEntityById(organizationId, packId);
        pack.setCurrentVersionId(versionId);
        Pack saved = packRepository.save(pack);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.PACK_CURRENT_VERSION_SET,
                packId, organizationId, versionId);
        return saved;
    }

    @Override
    @Transactional
    public Pack setRequired(UUID organizationId, UUID packId, boolean required) {
        Pack pack = packQueryService.getEntityById(organizationId, packId);
        pack.setRequired(required);
        Pack saved = packRepository.save(pack);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.PACK_REQUIRED_SET,
                packId, organizationId, required);
        return saved;
    }

    /**
     * The first condition under which something depends on the pack's name, cheapest check first.
     * A required pack or a composed pack (itself or a pack below it, whose chain brings it in) is
     * delivered under its name; a version past DRAFT may have been reviewed or released; a draft
     * file in the name's namespace would leave that namespace on a rename; and a plan was matched
     * to the pack by its name.
     */
    private Optional<String> inUseCondition(UUID organizationId, Pack pack) {
        List<UUID> packIds = new ArrayList<>();
        packIds.add(pack.getId());
        packIds.addAll(packQueryService.getDescendantIds(organizationId, pack.getId()));
        List<Pack> packs = packIds.size() == 1 ? List.of(pack) : packQueryService.getEntitiesByIds(organizationId, packIds);
        if (packs.stream().anyMatch(candidate -> Boolean.TRUE.equals(candidate.getRequired()))) {
            return Optional.of(PackConstant.CONDITION_REQUIRED);
        }
        if (packIds.stream().anyMatch(id -> !repositoryPackQueryService.getRepositoryIdsOfPack(organizationId, id).isEmpty())) {
            return Optional.of(PackConstant.CONDITION_COMPOSED);
        }
        List<Version> versions = versionQueryService.getEntitiesOfPack(organizationId, pack.getId());
        if (pack.getCurrentVersionId() != null
                || versions.stream().anyMatch(version -> version.getStatus() != VersionStatus.DRAFT)) {
            return Optional.of(PackConstant.CONDITION_VERSIONED);
        }
        for (Version draft : versions) {
            for (RenderedFile file : versionQueryService.render(organizationId, draft)) {
                if (isUnderName(file.path(), pack.getName())) {
                    return Optional.of(PackConstant.CONDITION_DRAFT_NAMESPACE_FILES);
                }
            }
        }
        if (planQueryService.existsForPack(organizationId, pack.getId())) {
            return Optional.of(PackConstant.CONDITION_PLANS);
        }
        return Optional.empty();
    }

    /**
     * Whether a path has a folder or file named after the pack, or one prefixed with the name and a
     * hyphen. That covers every form of a mounted pack's namespace (.agents/rules/p/,
     * .agents/skills/p-x/, .claude/agents/p-x.md and the rest) without needing a mount folder, and
     * errs towards keeping the name for a path that only looks like one.
     */
    private static boolean isUnderName(String path, String name) {
        String prefix = name + PackConstant.NAMESPACE_NAME_SEPARATOR;
        return Arrays.stream(path.split(PackConstant.PATH_SEPARATOR))
                .anyMatch(segment -> segment.equals(name) || segment.startsWith(prefix));
    }

    /**
     * The pack with the folders it is mounted in. A renamed pack is composed nowhere, so its list is
     * empty, while a pack whose description changed may be mounted anywhere.
     */
    private PackResponse withMountPaths(UUID organizationId, Pack pack) {
        List<String> mountPaths = repositoryPackQueryService.getPackWithRepositories(organizationId, pack.getId()).mountPaths();
        return packMapper.toResponse(pack).withMountPaths(mountPaths);
    }

    private ConflictException nameTaken(UUID organizationId, String name) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.PACK_NAME_TAKEN, name, organizationId);
        return new ConflictException(ExceptionDomainKayaError.PACK_NAME_TAKEN, Map.of(PackConstant.FIELD_NAME, name));
    }
}
