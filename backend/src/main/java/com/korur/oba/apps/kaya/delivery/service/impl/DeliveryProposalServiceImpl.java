package com.korur.oba.apps.kaya.delivery.service.impl;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.delivery.DeliveryProposalProperties;
import com.korur.oba.apps.kaya.delivery.constant.DeliveryConstant;
import com.korur.oba.apps.kaya.delivery.constant.service.DeliveryServiceLogConstant;
import com.korur.oba.apps.kaya.delivery.dto.request.DeliveryProposalRequest;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryProposalResponse;
import com.korur.oba.apps.kaya.delivery.mapper.DeliveryMapper;
import com.korur.oba.apps.kaya.delivery.service.DeliveryProposalService;
import com.korur.oba.apps.kaya.delivery.service.DeliveryQueryService;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.release.dto.embedded.PackVersionEntry;
import com.korur.oba.apps.kaya.release.dto.embedded.ReleaseEntry;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.helper.MountPaths;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.dto.embedded.DeliveredFile;
import com.korur.oba.apps.kaya.version.dto.request.ProposalFileRequest;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;
import com.korur.oba.apps.kaya.version.service.VersionService;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.agent.validator.AgentValidator;

/**
 * The {@link DeliveryProposalService} implementation: it resolves the proposer and the owner of the
 * proposed paths and leaves the draft, its checks and the submit to
 * {@link VersionService#propose}, all in one transaction.
 */
@Service
@RequiredArgsConstructor
public class DeliveryProposalServiceImpl implements DeliveryProposalService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DeliveryProposalServiceImpl.class);

    private final DeliveryQueryService deliveryQueryService;
    private final ReleaseQueryService releaseQueryService;
    private final PackQueryService packQueryService;
    private final VersionService versionService;
    private final VersionQueryService versionQueryService;
    private final ValidationQueryService validationQueryService;
    private final DeliveryMapper deliveryMapper;
    private final DeliveryProposalProperties deliveryProposalProperties;
    private final KayaUserQueryService kayaUserQueryService;
    private final AgentValidator agentValidator;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public DeliveryProposalResponse propose(MachinePrincipal principal, DeliveryProposalRequest request) {
        UUID organizationId = principal.organizationId();
        // The user of the token becomes the proposer: a member who answers for the change and whom
        // the review rules can exclude from approving it. The machine token chain has checked that the
        // user still acts in the organization with the Kaya app.
        UUID proposerId = principal.userId();
        // An approved proposal releases hooks and settings to every machine of the organization, and
        // an agent's run reads text other people write, so an agent proposes nothing. Refused before
        // any lookup, so an agent gets the same 403 whatever the request names.
        agentValidator.ensureCallerIsNotAgent(kayaUserQueryService.getEntityById(proposerId),
                AgentConstant.ACTION_KAYA_CONFIG_PROPOSE);
        requireDistinctPaths(principal, request.files());
        RepositoryResponse repository = deliveryQueryService.getRepository(organizationId, request.remoteUrl());
        Release release = releaseQueryService.getEntityByNumber(organizationId, repository.id(),
                request.releaseNumber());

        Map<String, ReleaseEntry> manifest = release.getManifest().stream()
                .collect(Collectors.toMap(ReleaseEntry::path, Function.identity()));
        Map<UUID, String> mounts = mountsOf(release);
        Map<UUID, Pack> mountedPacks = packQueryService.getEntitiesByIds(organizationId, mounts.keySet()).stream()
                .collect(Collectors.toMap(Pack::getId, Function.identity()));
        Map<UUID, List<String>> pathsByOwner = new LinkedHashMap<>();
        Map<String, DeliveredFile> delivered = new HashMap<>();
        List<ProposalFileRequest> stored = new ArrayList<>();
        for (ProposalFileRequest file : request.files()) {
            ReleaseEntry entry = manifest.get(file.path());
            UUID ownerId = entry != null
                    ? entry.ownerId()
                    : mountedOwnerOf(file.path(), mounts, mountedPacks)
                            .orElseGet(() -> deepestRootPack(organizationId, repository.id(), release));
            String storedPath = storedPathOf(file.path(), mounts.get(ownerId));
            if (entry != null) {
                delivered.put(storedPath, deliveryMapper.toDeliveredFile(entry));
            }
            stored.add(deliveryMapper.toStoredFile(file, storedPath));
            pathsByOwner.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(file.path());
        }
        if (pathsByOwner.size() > 1) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.PROPOSAL_SPANS_OWNERS,
                    principal.machineId(), release.getId(), pathsByOwner.size());
            Map<String, List<String>> owners = new LinkedHashMap<>();
            pathsByOwner.forEach((ownerId, paths) -> owners.put(ownerId.toString(), paths));
            throw new ValidationException(ExceptionDomainKayaError.DELIVERY_PROPOSAL_SPANS_OWNERS,
                    Map.of(DeliveryConstant.FIELD_OWNERS, owners));
        }

        UUID ownerId = pathsByOwner.keySet().iterator().next();
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.PROPOSAL_RESOLVED,
                principal.machineId(), release.getId(), repository.id(), ownerId);
        // A manifest entry names its owner by id only, and every owner is a pack.
        Pack pack = packQueryService.getEntityById(organizationId, ownerId);
        String name = pack.getName();
        VersionResponse version = versionService.propose(proposerId, organizationId, ownerId, delivered, stored,
                request.reason());
        // A proposal goes to review without a submit, so it is checked here as a submit is: against
        // every repository that delivers its owner. A refusal rolls the proposal back whole.
        Version proposed = versionQueryService.getEntityById(organizationId, version.id());
        List<Violation> violations = releaseQueryService.validateVersion(organizationId, proposed);
        if (!violations.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.PROPOSAL_BREAKS_RULES,
                    principal.machineId(), version.id(), violations.size());
        }
        validationQueryService.requireValid(violations);
        return deliveryMapper.toProposalResponse(version, name, release.getNumber(), reviewUrlOf(version.id()));
    }

    private void requireDistinctPaths(MachinePrincipal principal, List<ProposalFileRequest> files) {
        Set<String> seen = new HashSet<>();
        Set<String> twice = new TreeSet<>();
        for (ProposalFileRequest file : files) {
            if (!seen.add(file.path())) {
                twice.add(file.path());
            }
        }
        if (!twice.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.PROPOSAL_DUPLICATE_PATHS,
                    principal.machineId(), twice.size());
            throw new ValidationException(ExceptionValidationError.VALIDATION,
                    Map.of(DeliveryConstant.FIELD_PATHS, List.copyOf(twice)));
        }
    }

    /** The mount folder of each pack the release mounts, by pack id. */
    private static Map<UUID, String> mountsOf(Release release) {
        Map<UUID, String> mounts = new LinkedHashMap<>();
        if (release.getPackVersions() == null) {
            return mounts;
        }
        for (PackVersionEntry entry : release.getPackVersions()) {
            if (entry.rootPath() != null) {
                mounts.put(entry.packId(), entry.rootPath());
            }
        }
        return mounts;
    }

    /**
     * The mounted pack whose namespace holds a path the release did not deliver yet, its folder's
     * AGENTS.md included. Pack names may share a prefix (a and a-b both claim .agents/skills/a-b-x/),
     * so the longest name that matches wins: it is the most specific claim.
     */
    private static Optional<UUID> mountedOwnerOf(String path, Map<UUID, String> mounts, Map<UUID, Pack> packs) {
        UUID owner = null;
        int longest = -1;
        for (Map.Entry<UUID, String> mount : mounts.entrySet()) {
            Pack pack = packs.get(mount.getKey());
            if (pack != null && pack.getName().length() > longest
                    && MountPaths.isInNamespace(path, pack.getName(), mount.getValue())) {
                owner = pack.getId();
                longest = pack.getName().length();
            }
        }
        return Optional.ofNullable(owner);
    }

    /**
     * The owner of a new path no mounted pack's namespace holds: the deepest pack the repository
     * composes at its root, the most specific config the repository has. Two packs at that depth
     * are siblings and neither is more specific, so the proposal is refused with both as candidates
     * rather than given to a pack the member did not choose.
     */
    private UUID deepestRootPack(UUID organizationId, UUID repositoryId, Release release) {
        List<UUID> candidates = new ArrayList<>();
        int deepest = 0;
        List<PackVersionEntry> entries = release.getPackVersions() == null ? List.of() : release.getPackVersions();
        for (PackVersionEntry entry : entries) {
            if (entry.rootPath() != null) {
                continue;
            }
            int depth = packQueryService.getChain(organizationId, entry.packId()).size();
            if (depth > deepest) {
                deepest = depth;
                candidates.clear();
            }
            if (depth == deepest) {
                candidates.add(entry.packId());
            }
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, DeliveryServiceLogConstant.PROPOSAL_NEW_PATH_NO_OWNER,
                repositoryId, organizationId, candidates.size());
        throw new ValidationException(ExceptionDomainKayaError.DELIVERY_PROPOSAL_SPANS_OWNERS,
                Map.of(DeliveryConstant.FIELD_CANDIDATES, candidates.stream().map(UUID::toString).toList()));
    }

    /**
     * The path a mounted pack stores a file at: its AGENTS.md is delivered in the mount folder but
     * stored at AGENTS.md, so the same pack can be mounted in any folder; every other path is
     * stored as delivered.
     */
    private static String storedPathOf(String path, String rootPath) {
        if (rootPath != null && path.equals(MountPaths.agentsFile(rootPath))) {
            return ValidationConstant.AGENTS_FILE;
        }
        return path;
    }

    private String reviewUrlOf(UUID versionId) {
        String webUrl = deliveryProposalProperties.webUrl();
        String origin = webUrl.endsWith(DeliveryConstant.URL_PATH_SEPARATOR)
                ? webUrl.substring(0, webUrl.length() - DeliveryConstant.URL_PATH_SEPARATOR.length())
                : webUrl;
        return origin + DeliveryConstant.REVIEW_PATH + versionId;
    }
}
