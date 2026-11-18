package com.korur.oba.apps.kaya.seed.service.impl;

// 1. Framework-level
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.constant.BlobConstant;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.pack.dto.request.PackCreateRequest;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.pack.service.PackService;
import com.korur.oba.apps.kaya.release.helper.Composition;
import com.korur.oba.apps.kaya.release.helper.PackMount;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.release.service.ReleaseService;
import com.korur.oba.apps.kaya.release.service.RepositoryPackQueryService;
import com.korur.oba.apps.kaya.release.service.RepositoryPackService;
import com.korur.oba.apps.kaya.render.dto.embedded.FileRefusal;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.helper.FileCandidates;
import com.korur.oba.apps.kaya.render.service.RenderQueryService;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.service.RepositoryService;
import com.korur.oba.apps.kaya.seed.constant.SeedConstant;
import com.korur.oba.apps.kaya.seed.constant.service.SeedServiceLogConstant;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedNotImported;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedRefusedFile;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedReleaseReport;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedReport;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedSetReport;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedWarning;
import com.korur.oba.apps.kaya.seed.dto.request.SeedFileRequest;
import com.korur.oba.apps.kaya.seed.dto.request.SeedRepositoryPackRequest;
import com.korur.oba.apps.kaya.seed.dto.request.SeedRepositoryRequest;
import com.korur.oba.apps.kaya.seed.dto.request.SeedRequest;
import com.korur.oba.apps.kaya.seed.dto.request.SeedSetRequest;
import com.korur.oba.apps.kaya.seed.dto.response.SeedRunResponse;
import com.korur.oba.apps.kaya.seed.entity.SeedRun;
import com.korur.oba.apps.kaya.seed.enums.SeedRunStatus;
import com.korur.oba.apps.kaya.seed.enums.SeedSetOutcome;
import com.korur.oba.apps.kaya.seed.helper.SeedRunRecorder;
import com.korur.oba.apps.kaya.seed.mapper.SeedMapper;
import com.korur.oba.apps.kaya.seed.repository.SeedRunRepository;
import com.korur.oba.apps.kaya.seed.service.SeedService;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.helper.FileCandidate;
import com.korur.oba.apps.kaya.validation.helper.MountPaths;
import com.korur.oba.apps.kaya.validation.helper.PathRules;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.service.VersionService;

/**
 * The seed in five steps, all in one transaction under a per-organization lock: refuse a malformed
 * request before anything is written; screen every pack's files with the file rules and with what
 * render can store, and leave out the ones that fail, and every link that leads to or through one
 * of them; create the missing packs under their parents, write each named repository's pack list
 * with its mount folders, and a SEED version for each pack whose files differ from its current
 * version; validate the composition of every repository the run reaches; then sign one release per
 * such repository. A refused composition rolls the transaction back and the run is stored on its
 * own, so nothing a seed releases was refused anywhere. Size limits are the one difference from a
 * change proposal: a seed mirrors today's files, so a file over a limit is imported
 * and reported as a warning. A re-seeded pack keeps the override marker of every file it still has.
 *
 * Two refusals answer at once instead of a stored report, because the request itself cannot be
 * imported as it stands: a pack stored with another parent than the request names (a parent is
 * fixed at creation), and a rule of a mounted pack that is not scoped to each of its folders (the
 * CLI must not get a release that loads the rule in every folder).
 */
@Service
@RequiredArgsConstructor
public class SeedServiceImpl implements SeedService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SeedServiceImpl.class);

    private final SeedRunRepository seedRunRepository;
    private final SeedRunRecorder seedRunRecorder;
    private final SeedMapper seedMapper;
    private final BlobQueryService blobQueryService;
    private final PackQueryService packQueryService;
    private final PackService packService;
    private final RepositoryPackQueryService repositoryPackQueryService;
    private final RepositoryPackService repositoryPackService;
    private final RepositoryService repositoryService;
    private final RenderQueryService renderQueryService;
    private final ValidationQueryService validationQueryService;
    private final VersionService versionService;
    private final ReleaseQueryService releaseQueryService;
    private final ReleaseService releaseService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public SeedRunResponse seed(User owner, UUID organizationId, SeedRequest request) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEEDING,
                owner.getId(), organizationId, request.sourceRef(), request.sets().size());
        checkFileCount(organizationId, request);
        seedRunRepository.lockSeeds(organizationId.toString());

        checkRequest(organizationId, request);
        Map<String, byte[]> contents = loadContents(organizationId, request);

        List<SetWork> works = request.sets().stream().map(SetWork::new).toList();
        List<RepositoryWork> repositories = connectRepositories(organizationId, request);
        for (SetWork work : works) {
            screenFiles(organizationId, work, contents);
        }
        leaveOutLinksToRefused(organizationId, works);
        resolveOwners(organizationId, works);

        String reason = String.format(SeedConstant.VERSION_REASON_FORMAT, request.sourceRef());
        lockOwners(organizationId, works, repositories);
        lockRepositories(organizationId, works, repositories);
        createOwners(owner, organizationId, works);
        writePacks(organizationId, works, repositories);
        requireScopedRules(organizationId, works);
        for (SetWork work : works) {
            writeVersion(owner, organizationId, work, reason);
        }
        Set<UUID> reached = collectRepositories(organizationId, works, repositories);

        // Every reached repository is locked already, unless a pack change made it compose a pack
        // of this run after lockRepositories read the pack rows. The lock is re-entrant, so this
        // takes only the ones still missing, in repository id order.
        reached.forEach(releaseService::lockReleases);
        List<Composition> compositions = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();
        works.forEach(work -> violations.addAll(work.unattributed));
        violations.addAll(lockViolations(organizationId, works));
        for (UUID repositoryId : reached) {
            Composition composition = releaseQueryService.previewComposition(organizationId, repositoryId, Map.of());
            violations.addAll(validationQueryService.validateComposition(composition.toCandidate()));
            compositions.add(composition);
        }
        if (!violations.isEmpty()) {
            return discard(owner, organizationId, request, works, violations);
        }

        List<SeedReleaseReport> releases = new ArrayList<>();
        for (Composition composition : compositions) {
            causeOf(composition, works)
                    .flatMap(version -> releaseService.releaseRepository(organizationId, composition.repositoryId(), version.getId()))
                    .ifPresent(release -> releases.add(seedMapper.toReleaseReport(release)));
        }

        SeedReport report = report(true, request, works, releases, List.of());
        // Refused files, left-out links and warnings are reported, and the rest is released as it
        // is today. Only drift fails an applied run: its release cannot equal the repository.
        boolean passed = report.drift().isEmpty();
        SeedRun run = seedRunRepository.save(SeedRun.builder()
                .organizationId(organizationId)
                .userId(owner.getId())
                .sourceRef(request.sourceRef())
                .status(passed ? SeedRunStatus.PASSED : SeedRunStatus.FAILED)
                .report(report)
                .build());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_COMPLETED,
                run.getId(), organizationId, run.getStatus(), releases.size());
        return seedMapper.toResponse(run);
    }

    // --- Request checks: nothing is written when one fails ---

    /**
     * Refuses a run with more files in all than one run takes, before it waits for the seed lock.
     * The per-set limit alone would let one request name a million files.
     */
    private void checkFileCount(UUID organizationId, SeedRequest request) {
        long files = request.sets().stream().mapToLong(set -> set.files().size()).sum();
        if (files > SeedConstant.SEED_FILES_MAX) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_TOO_MANY_FILES,
                    organizationId, files, SeedConstant.SEED_FILES_MAX);
            throw new ApplicationException(HttpStatus.PAYLOAD_TOO_LARGE, ExceptionDomainKayaError.SEED_TOO_LARGE,
                    Map.of(SeedConstant.FIELD_LIMIT_FILES, SeedConstant.SEED_FILES_MAX,
                            SeedConstant.FIELD_FILE_COUNT, files));
        }
    }

    private void checkRequest(UUID organizationId, SeedRequest request) {
        Set<String> setKeys = new HashSet<>();
        Map<String, String> parents = new HashMap<>();
        for (SeedSetRequest set : request.sets()) {
            if (!setKeys.add(set.kind() + SeedConstant.SET_KEY_SEPARATOR + set.name())) {
                throw refused(organizationId, SeedConstant.FIELD_SETS,
                        String.format(SeedConstant.SET_LISTED_TWICE_MESSAGE, set.kind(), set.name()));
            }
            parents.put(set.name(), set.parent());
            Set<String> paths = new HashSet<>();
            for (SeedFileRequest file : set.files()) {
                if (!paths.add(file.path())) {
                    throw refused(organizationId, SeedConstant.FIELD_FILES,
                            String.format(SeedConstant.PATH_LISTED_TWICE_MESSAGE, set.name(), file.path()));
                }
            }
        }
        checkParents(organizationId, parents);
        for (SeedRepositoryRequest repository : repositories(request)) {
            for (SeedRepositoryPackRequest pack : repository.packs()) {
                if (!parents.containsKey(pack.name()) && packQueryService.findByName(organizationId, pack.name()).isEmpty()) {
                    throw refused(organizationId, SeedConstant.FIELD_REPOSITORIES, String.format(
                            SeedConstant.REPOSITORY_PACK_UNKNOWN_MESSAGE, repository.name(), pack.name()));
                }
            }
        }
    }

    /**
     * Refuses a parent that is the pack itself, that neither the request nor the organization has,
     * or that closes a cycle through the request's packs. A stored pack's parent cannot form one, so
     * only the request's own links are followed; a stored parent that differs from the request is
     * refused later, once the stored packs are read.
     *
     * @param parents the parent name of each set by its pack name, null for a root pack
     */
    private void checkParents(UUID organizationId, Map<String, String> parents) {
        for (Map.Entry<String, String> entry : parents.entrySet()) {
            String name = entry.getKey();
            String parent = entry.getValue();
            if (parent == null) {
                continue;
            }
            if (parent.equals(name)) {
                throw refused(organizationId, SeedConstant.FIELD_PARENT, String.format(SeedConstant.PARENT_IS_SELF_MESSAGE, name));
            }
            if (!parents.containsKey(parent) && packQueryService.findByName(organizationId, parent).isEmpty()) {
                throw refused(organizationId, SeedConstant.FIELD_PARENT,
                        String.format(SeedConstant.PARENT_UNKNOWN_MESSAGE, name, parent));
            }
            Set<String> seen = new HashSet<>();
            String current = name;
            while (current != null && parents.containsKey(current)) {
                if (!seen.add(current)) {
                    throw refused(organizationId, SeedConstant.FIELD_PARENT,
                            String.format(SeedConstant.PARENT_CYCLE_MESSAGE, name));
                }
                current = parents.get(current);
            }
        }
    }

    private static List<SeedRepositoryRequest> repositories(SeedRequest request) {
        return request.repositories() == null ? List.of() : request.repositories();
    }

    /**
     * Resolves each named repository from its remote, connecting it as ingest would. Two remotes
     * that normalise to one repository refuse the run, since each would replace the other's list.
     */
    private List<RepositoryWork> connectRepositories(UUID organizationId, SeedRequest request) {
        List<RepositoryWork> works = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (SeedRepositoryRequest repositoryRequest : repositories(request)) {
            Repository repository = repositoryService.connectRepository(organizationId, repositoryRequest.remoteUrl(),
                    repositoryRequest.name());
            if (!seen.add(repository.getId())) {
                // The normalised remote, never the raw one: a raw remote may carry an access token.
                throw refused(organizationId, SeedConstant.FIELD_REPOSITORIES,
                        String.format(SeedConstant.REPOSITORY_LISTED_TWICE_MESSAGE, repository.getRemoteUrl()));
            }
            works.add(new RepositoryWork(repositoryRequest, repository.getId()));
        }
        return works;
    }

    /**
     * Loads the content of every named blob: the file rules read it, and render stores it. The
     * content is measured first, so a run that names more than the limit is refused before any of
     * it is loaded. A hash the organization does not have refuses the run with every missing hash,
     * so the CLI can upload exactly those and run again.
     */
    private Map<String, byte[]> loadContents(UUID organizationId, SeedRequest request) {
        Set<String> named = request.sets().stream()
                .flatMap(set -> set.files().stream())
                .map(SeedFileRequest::sha256)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        if (named.isEmpty()) {
            return new HashMap<>();
        }
        long contentBytes = blobQueryService.sumSizes(organizationId, named);
        if (contentBytes > SeedConstant.SEED_CONTENT_MAX_BYTES) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_CONTENT_TOO_LARGE,
                    organizationId, contentBytes, SeedConstant.SEED_CONTENT_MAX_BYTES);
            throw new ApplicationException(HttpStatus.PAYLOAD_TOO_LARGE, ExceptionDomainKayaError.SEED_TOO_LARGE,
                    Map.of(BlobConstant.FIELD_LIMIT_BYTES, SeedConstant.SEED_CONTENT_MAX_BYTES,
                            SeedConstant.FIELD_CONTENT_BYTES, contentBytes));
        }
        Map<String, byte[]> contents = new HashMap<>(blobQueryService.getContents(organizationId, named));
        List<String> missing = named.stream().filter(sha256 -> !contents.containsKey(sha256)).toList();
        if (!missing.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_BLOBS_MISSING,
                    organizationId, missing.size());
            throw new ValidationException(ExceptionDomainKayaError.SEED_BLOBS_MISSING,
                    Map.of(SeedConstant.FIELD_MISSING, missing));
        }
        return contents;
    }

    private ValidationException refused(UUID organizationId, String field, String message) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_REQUEST_REFUSED,
                organizationId, message);
        return new ValidationException(ExceptionValidationError.VALIDATION, Map.of(field, message));
    }

    // --- File screening ---

    /**
     * Applies the file rules to a set and leaves out each file that breaks one. The rules of a file
     * on its own (path, mode, target, content) run once per file; a file that breaks only size
     * limits is imported and its violations are kept as warnings. What render cannot store (a path
     * no kind takes, a file that does not parse, a skill directory without SKILL.md) is left out
     * next, with its reason. The link rules depend on the other files, and leaving a file out can
     * break another one (a file below a link that pointed into it), so only they repeat on the
     * remaining files until those keep them; each round removes at least one file. A violation that
     * names no file of the set cannot be resolved by leaving a file out and refuses the run's
     * composition.
     */
    private void screenFiles(UUID organizationId, SetWork work, Map<String, byte[]> contents) {
        List<SeedFile> accepted = new ArrayList<>();
        for (SeedFileRequest file : work.request.files()) {
            SeedFile seedFile = SeedFile.of(file, file.sha256() == null ? null : contents.get(file.sha256()));
            List<Violation> violations = new ArrayList<>(validationQueryService.validateFile(seedFile.candidate()));
            if (violations.stream().allMatch(violation -> violation.rule().isSizeLimit())) {
                accepted.add(seedFile);
                if (!violations.isEmpty()) {
                    work.warnings.put(file.path(), violations);
                }
            } else {
                work.refused.put(file.path(), Refusal.of(violations));
            }
        }
        for (FileRefusal refusal : renderQueryService.screen(accepted.stream().map(SeedFile::toSource).toList())) {
            work.refused.put(refusal.path(), Refusal.of(refusal));
        }
        accepted.removeIf(file -> work.refused.containsKey(file.path()));

        while (true) {
            List<Violation> violations = validationQueryService.validateLinks(accepted.stream().map(SeedFile::candidate).toList());
            if (violations.isEmpty()) {
                break;
            }
            Set<String> paths = accepted.stream().map(SeedFile::path).collect(Collectors.toSet());
            List<Violation> unattributed = new ArrayList<>();
            for (Violation violation : violations) {
                if (violation.path() != null && paths.contains(violation.path())) {
                    work.refused.computeIfAbsent(violation.path(), path -> Refusal.of(new ArrayList<>()))
                            .violations().add(violation);
                } else {
                    unattributed.add(violation);
                }
            }
            int before = accepted.size();
            accepted.removeIf(file -> work.refused.containsKey(file.path()));
            if (accepted.size() == before) {
                work.unattributed.addAll(unattributed);
                break;
            }
        }
        work.accepted = accepted;
        work.warnings.keySet().removeIf(work.refused::containsKey);
        work.refused.forEach((path, refusal) -> loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                SeedServiceLogConstant.SEED_FILE_REFUSED, organizationId, path, work.request.kind(), work.request.name(),
                refusal.errorCode() != null ? refusal.errorCode() : refusal.violations().stream().map(Violation::rule).toList()));
        work.warnings.forEach((path, violations) -> loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                SeedServiceLogConstant.SEED_FILE_WARNED, organizationId, path, work.request.kind(), work.request.name(),
                violations.stream().map(Violation::rule).toList()));
    }

    /**
     * Leaves out every imported link that the refused files would leave dangling, so one refused
     * file cannot fail every repository that composes it: a link whose target, followed through the
     * run's imported links, is no longer delivered by the run and is, lies below or holds a refused
     * path. A link to .agents/skills/x whose only file was refused is one; a link to a skill
     * directory that still delivers its SKILL.md is kept, even when a file inside it was refused.
     * A left-out link counts as refused for the links that lead through it, so this repeats until
     * nothing more is left out. Each is reported as not imported, with the refused path it needs.
     */
    private void leaveOutLinksToRefused(UUID organizationId, List<SetWork> works) {
        Set<String> delivered = new HashSet<>();
        Map<String, String> links = new HashMap<>();
        Set<String> refused = new TreeSet<>();
        for (SetWork work : works) {
            for (SeedFile file : work.accepted) {
                delivered.add(file.path());
                if (file.mode() == FileMode.SYMLINK) {
                    links.putIfAbsent(file.path(), file.symlinkTarget());
                }
            }
            refused.addAll(work.refused.keySet());
        }
        refused.removeAll(delivered);
        boolean changed = !refused.isEmpty();
        while (changed) {
            changed = false;
            for (SetWork work : works) {
                for (SeedFile file : List.copyOf(work.accepted)) {
                    if (file.mode() != FileMode.SYMLINK) {
                        continue;
                    }
                    String resolved = PathRules.resolveSymlink(file.path(), file.symlinkTarget(), links).path();
                    if (resolved == null || isDelivered(resolved, delivered)) {
                        continue;
                    }
                    Optional<String> cause = refused.stream().filter(path -> isRelated(resolved, path)).findFirst();
                    if (cause.isEmpty()) {
                        continue;
                    }
                    work.accepted.remove(file);
                    work.leftOut.put(file.path(), String.format(SeedConstant.LINK_LEFT_OUT_REASON, work.request.kind(),
                            work.request.name(), resolved, cause.get()));
                    delivered.remove(file.path());
                    links.remove(file.path());
                    refused.add(file.path());
                    changed = true;
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_LINK_LEFT_OUT,
                            organizationId, file.path(), work.request.kind(), work.request.name(), cause.get());
                }
            }
        }
    }

    /** A resolved link target is never a link itself, so a delivered file or a file below it is enough. */
    private static boolean isDelivered(String path, Set<String> delivered) {
        String directory = path + ValidationConstant.PATH_SEPARATOR;
        return delivered.contains(path) || delivered.stream().anyMatch(file -> file.startsWith(directory));
    }

    /** Whether one path is the other or lies below it. */
    private static boolean isRelated(String first, String second) {
        return first.equals(second) || first.startsWith(second + ValidationConstant.PATH_SEPARATOR)
                || second.startsWith(first + ValidationConstant.PATH_SEPARATOR);
    }

    // --- Owners, versions and packs ---

    /**
     * Finds each stored pack by name and refuses one whose stored parent is not the parent the
     * request names: a pack's parent is fixed at creation, so a seed never moves a pack in the tree.
     * Packs still missing are created later, under the locks, by createOwners.
     */
    private void resolveOwners(UUID organizationId, List<SetWork> works) {
        for (SetWork work : works) {
            packQueryService.findByName(organizationId, work.request.name()).ifPresent(pack -> {
                work.takePack(pack);
                work.storedParentPackId = pack.getParentPackId();
            });
        }
        Map<String, SetWork> byName = byName(works);
        for (SetWork work : works) {
            if (work.packId == null) {
                continue;
            }
            String parent = work.request.parent();
            Optional<UUID> requested = parent == null ? Optional.empty() : parentIdOf(organizationId, parent, byName);
            boolean differs = parent != null && requested.isEmpty()
                    || !Objects.equals(requested.orElse(null), work.storedParentPackId);
            if (differs) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_PARENT_FIXED,
                        organizationId, work.name, work.storedParentPackId, parent);
                Map<String, Object> values = new LinkedHashMap<>();
                values.put(SeedConstant.FIELD_NAME, work.name);
                values.put(SeedConstant.FIELD_PARENT, parent == null ? "" : parent);
                values.put(SeedConstant.FIELD_STORED_PARENT_PACK_ID,
                        work.storedParentPackId == null ? "" : work.storedParentPackId.toString());
                throw new ConflictException(ExceptionDomainKayaError.SEED_PARENT_FIXED, values);
            }
        }
    }

    /** The id of a parent pack by name: the request's set once it has a pack, else the organization's pack. */
    private Optional<UUID> parentIdOf(UUID organizationId, String parent, Map<String, SetWork> byName) {
        SetWork set = byName.get(parent);
        if (set != null) {
            return Optional.ofNullable(set.packId);
        }
        return packQueryService.findByName(organizationId, parent).map(Pack::getId);
    }

    /** The id of a pack a repository names: the request's set, else the organization's pack. */
    private UUID packIdOf(UUID organizationId, String name, Map<String, SetWork> byName) {
        SetWork set = byName.get(name);
        if (set != null) {
            return set.packId;
        }
        return packQueryService.findByName(organizationId, name).map(Pack::getId).orElseThrow();
    }

    private static Map<String, SetWork> byName(List<SetWork> works) {
        Map<String, SetWork> byName = new HashMap<>();
        works.forEach(work -> byName.put(work.request.name(), work));
        return byName;
    }

    /**
     * Locks, in id order and before any version is numbered, every stored pack of the run and every
     * stored pack whose repository rows the run may write: each pack a named repository uses now
     * and each it will use. An approval of such a pack takes the same lock, so the two never number
     * versions of one pack at the same time, and it sees a repository's pack list either before or
     * after this run. A pack the run creates has no row yet and needs no lock: nothing else can
     * reach it before the run commits.
     */
    private void lockOwners(UUID organizationId, List<SetWork> works, List<RepositoryWork> repositories) {
        Set<UUID> locked = new TreeSet<>();
        for (SetWork work : works) {
            if (work.packId != null) {
                locked.add(work.packId);
            }
        }
        for (RepositoryWork repository : repositories) {
            repository.request.packs().stream()
                    .map(pack -> packQueryService.findByName(organizationId, pack.name()))
                    .flatMap(Optional::stream)
                    .forEach(pack -> locked.add(pack.getId()));
            repositoryPackQueryService.getMounts(organizationId, repository.repositoryId)
                    .forEach(mount -> locked.add(mount.packId()));
        }
        locked.forEach(releaseService::lockOwner);
    }

    /**
     * Takes the release lock of every repository the run may change, in repository id order, before
     * any version or pack row is written: each named repository, and each repository that uses a
     * stored pack of the request or one of its descendants, since a pack reaches every repository
     * whose chains hold it. A pack change of the same repository takes this lock too, so the two
     * apply one after the other.
     */
    private void lockRepositories(UUID organizationId, List<SetWork> works, List<RepositoryWork> repositories) {
        Set<UUID> repositoryIds = new TreeSet<>();
        for (SetWork work : works) {
            if (work.packId == null) {
                continue;
            }
            repositoryIds.addAll(repositoryPackQueryService.getRepositoryIdsOfPack(organizationId, work.packId));
            for (UUID descendantId : packQueryService.getDescendantIds(organizationId, work.packId)) {
                repositoryIds.addAll(repositoryPackQueryService.getRepositoryIdsOfPack(organizationId, descendantId));
            }
        }
        repositories.forEach(repository -> repositoryIds.add(repository.repositoryId));
        repositoryIds.forEach(releaseService::lockReleases);
    }

    /**
     * Creates each pack the organization does not have yet under the parent the request names, a
     * parent before its children. The request checks rule out a cycle and an unknown parent, so
     * every round creates at least one pack.
     */
    private void createOwners(User owner, UUID organizationId, List<SetWork> works) {
        Map<String, SetWork> byName = byName(works);
        List<SetWork> pending = new ArrayList<>(works.stream().filter(work -> work.packId == null).toList());
        while (!pending.isEmpty()) {
            List<SetWork> created = new ArrayList<>();
            for (SetWork work : pending) {
                String parent = work.request.parent();
                Optional<UUID> parentId = parent == null ? Optional.empty() : parentIdOf(organizationId, parent, byName);
                if (parent != null && parentId.isEmpty()) {
                    continue;
                }
                UUID packId = packService.createPack(owner, organizationId,
                        new PackCreateRequest(work.request.name(), null, parentId.orElse(null))).id();
                work.takePack(packQueryService.getEntityById(organizationId, packId));
                work.created = true;
                created.add(work);
            }
            if (created.isEmpty()) {
                throw new IllegalStateException(SeedConstant.PARENT_ORDER_BROKEN_MESSAGE);
            }
            pending.removeAll(created);
        }
    }

    /**
     * Replaces the pack list of each named repository with the request's packs and mount folders.
     * The folders are checked as a pack list change checks them; the compositions are validated
     * with the rest of the run.
     */
    private void writePacks(UUID organizationId, List<SetWork> works, List<RepositoryWork> repositories) {
        Map<String, SetWork> byName = byName(works);
        for (RepositoryWork repository : repositories) {
            List<PackMount> mounts = repository.request.packs().stream()
                    .map(pack -> new PackMount(packIdOf(organizationId, pack.name(), byName), pack.rootPath()))
                    .toList();
            repository.changed = repositoryPackService.writePacks(organizationId, repository.repositoryId, mounts);
            if (repository.changed) {
                loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_PACKS_WRITTEN,
                        organizationId, repository.repositoryId);
            }
        }
    }

    /**
     * Refuses the run when a rule in a pack's namespace is not scoped to every folder the pack is
     * mounted at, in any repository, with this run's pack lists already written. A seed mirrors
     * the files on disk and never adds or rewrites paths frontmatter, so such a rule cannot be
     * imported as it is.
     */
    private void requireScopedRules(UUID organizationId, List<SetWork> works) {
        List<Violation> violations = new ArrayList<>();
        for (SetWork work : works) {
            for (SeedFile file : work.accepted) {
                if (MountPaths.isMountRule(file.path(), work.name) && PathRules.isScopableRule(file.path())) {
                    violations.addAll(releaseQueryService.getRuleScopeViolations(organizationId, work.packId, file.path(),
                            file.mode(), file.content()));
                }
            }
        }
        if (!violations.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_RULE_UNSCOPED,
                    organizationId, violations.size());
        }
        validationQueryService.requireValid(violations);
    }

    /**
     * Writes a SEED version when the screened files differ from the owner's current version; the
     * version service keeps the override markers and leaves an unchanged pack as it is.
     */
    private void writeVersion(User owner, UUID organizationId, SetWork work, String reason) {
        Optional<Version> version = versionService.seed(owner.getId(), organizationId, work.packId,
                work.accepted.stream().map(SeedFile::toSource).toList(), reason);
        if (version.isEmpty()) {
            work.outcome = SeedSetOutcome.UNCHANGED;
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_SET_UNCHANGED,
                    organizationId, work.request.kind(), work.name);
            return;
        }
        work.version = version.get();
        work.outcome = work.created || work.version.getBaseVersion() == null ? SeedSetOutcome.CREATED : SeedSetOutcome.UPDATED;
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_VERSION_CREATED,
                organizationId, work.version.getNumber(), work.request.kind(), work.name, work.accepted.size());
    }

    /**
     * Collects the repositories the run reaches: every repository whose chains hold a pack the run
     * changed, and every named repository whose pack list changed or that has no release yet.
     *
     * @return the reached repositories, in id order
     */
    private Set<UUID> collectRepositories(UUID organizationId, List<SetWork> works, List<RepositoryWork> repositories) {
        Set<UUID> reached = new TreeSet<>();
        for (SetWork work : works) {
            if (work.version != null) {
                List<UUID> composing = releaseQueryService.getComposingRepositoryIds(organizationId, work.version);
                work.composed = !composing.isEmpty();
                reached.addAll(composing);
            }
        }
        for (RepositoryWork repository : repositories) {
            if (repository.changed || releaseQueryService.findLatest(organizationId, repository.repositoryId).isEmpty()) {
                reached.add(repository.repositoryId);
            }
        }
        return reached;
    }

    /**
     * The items a changed pack that no repository composes stores though an ancestor locks them. A
     * composed pack's locks are checked by its repositories' compositions; this covers a pack no
     * repository uses yet, which may not store such an item either.
     */
    private List<Violation> lockViolations(UUID organizationId, List<SetWork> works) {
        List<Violation> violations = new ArrayList<>();
        for (SetWork work : works) {
            if (work.version == null || work.composed) {
                continue;
            }
            List<Violation> locked = releaseQueryService.getLockViolations(organizationId, work.packId,
                    work.accepted.stream().map(SeedFile::path).toList());
            if (!locked.isEmpty()) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_ITEMS_LOCKED,
                        organizationId, work.name, locked.size());
            }
            violations.addAll(locked);
        }
        return violations;
    }

    /**
     * The version a repository's release names: the first version of its composition this run
     * created, else the first version it composes.
     */
    private static Optional<Version> causeOf(Composition composition, List<SetWork> works) {
        Set<UUID> created = works.stream()
                .map(work -> work.version)
                .filter(Objects::nonNull)
                .map(Version::getId)
                .collect(Collectors.toSet());
        List<Version> versions = composition.versions();
        return versions.stream()
                .filter(version -> created.contains(version.getId()))
                .findFirst()
                .or(() -> versions.stream().findFirst());
    }

    // --- Outcome ---

    /**
     * Rolls back every write of the run and stores the run on its own, FAILED, with the violations.
     */
    private SeedRunResponse discard(User owner, UUID organizationId, SeedRequest request, List<SetWork> works,
                                    List<Violation> violations) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_COMPOSITION_REFUSED,
                organizationId, violations.size());
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        SeedRun run = seedRunRecorder.recordSeparately(SeedRun.builder()
                .organizationId(organizationId)
                .userId(owner.getId())
                .sourceRef(request.sourceRef())
                .status(SeedRunStatus.FAILED)
                .report(report(false, request, works, List.of(), violations))
                .build());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, SeedServiceLogConstant.SEED_COMPLETED,
                run.getId(), organizationId, run.getStatus(), 0);
        return seedMapper.toResponse(run);
    }

    private SeedReport report(boolean applied, SeedRequest request, List<SetWork> works,
                              List<SeedReleaseReport> releases, List<Violation> violations) {
        Map<EntityKind, Integer> entities = new EnumMap<>(EntityKind.class);
        List<SeedSetReport> sets = new ArrayList<>();
        List<SeedRefusedFile> refused = new ArrayList<>();
        List<SeedWarning> warnings = new ArrayList<>();
        List<SeedNotImported> notImported = new ArrayList<>(request.notImported() == null ? List.of() : request.notImported());
        for (SetWork work : works) {
            renderQueryService.countEntities(work.accepted.stream().map(SeedFile::toSource).toList())
                    .forEach((kind, count) -> entities.merge(kind, count, Integer::sum));
            work.refused.forEach((path, refusal) -> refused.add(seedMapper.toRefusedFile(work.request.kind(), work.request.name(),
                    path, refusal.errorCode(), refusal.reason(), refusal.violations())));
            work.warnings.values().forEach(rules -> rules.forEach(rule -> warnings.add(
                    seedMapper.toWarning(work.request.kind(), work.request.name(), rule))));
            work.leftOut.forEach((path, reason) -> notImported.add(seedMapper.toNotImported(path, reason)));
            // An owner this run created is gone once the run discards its writes.
            boolean kept = applied || !work.created;
            boolean versionKept = applied && work.version != null;
            sets.add(seedMapper.toSetReport(
                    kept ? work.packId : null,
                    work.request.kind(),
                    work.name,
                    work.outcome,
                    versionKept ? work.version.getId() : null,
                    versionKept ? work.version.getNumber() : null,
                    work.accepted.size(),
                    work.refused.size(),
                    work.version != null && Boolean.TRUE.equals(work.version.getExecutableChange())));
        }
        return seedMapper.toReport(applied, entities, sets, releases, refused, violations, warnings, notImported,
                request.drift() == null ? List.of() : request.drift());
    }

    /** One file the run imports: a file of the request with its uploaded content. */
    private record SeedFile(String path, FileMode mode, String symlinkTarget, String sha256, byte[] content) {

        static SeedFile of(SeedFileRequest file, byte[] content) {
            boolean link = file.mode() == FileMode.SYMLINK;
            return new SeedFile(file.path(), file.mode(), link ? file.symlinkTarget() : null, link ? null : file.sha256(),
                    link ? null : content);
        }

        FileCandidate candidate() {
            return FileCandidates.of(path, mode, symlinkTarget, content);
        }

        // The override marker is the version service's to keep, from the current version.
        SourceFile toSource() {
            return new SourceFile(path, mode, symlinkTarget, content, null);
        }
    }

    /**
     * Why a file was left out: the file rules it breaks, or the error code and reason render
     * refused it with.
     */
    private record Refusal(String errorCode, String reason, List<Violation> violations) {

        static Refusal of(List<Violation> violations) {
            return new Refusal(null, null, violations);
        }

        static Refusal of(FileRefusal refusal) {
            return new Refusal(refusal.errorCode(), refusal.reason(), new ArrayList<>());
        }
    }

    /** The state of one named repository while the run works on it. */
    private static final class RepositoryWork {

        private final SeedRepositoryRequest request;
        private final UUID repositoryId;
        private boolean changed;

        private RepositoryWork(SeedRepositoryRequest request, UUID repositoryId) {
            this.request = request;
            this.repositoryId = repositoryId;
        }
    }

    /** The state of one requested set while the run works on it. */
    private static final class SetWork {

        private final SeedSetRequest request;
        private final Map<String, Refusal> refused = new LinkedHashMap<>();
        private final Map<String, List<Violation>> warnings = new LinkedHashMap<>();
        private final Map<String, String> leftOut = new LinkedHashMap<>();
        private final List<Violation> unattributed = new ArrayList<>();
        private List<SeedFile> accepted = new ArrayList<>();
        private UUID packId;
        /** The stored name. */
        private String name;
        /** The parent of a stored pack, null for a stored root pack and for a pack the run creates. */
        private UUID storedParentPackId;
        private boolean created;
        /** Whether a repository composes the version the run wrote. */
        private boolean composed;
        private Version version;
        private SeedSetOutcome outcome;

        private SetWork(SeedSetRequest request) {
            this.request = request;
            this.name = request.name();
        }

        private void takePack(Pack pack) {
            packId = pack.getId();
            name = pack.getName();
        }
    }
}
