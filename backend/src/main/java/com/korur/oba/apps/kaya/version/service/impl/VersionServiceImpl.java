package com.korur.oba.apps.kaya.version.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.pack.service.PackService;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.render.enums.FileEncoding;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.helper.DeliveryPaths;
import com.korur.oba.apps.kaya.render.helper.RenderRefusals;
import com.korur.oba.apps.kaya.render.service.RenderService;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;
import com.korur.oba.apps.kaya.validation.helper.PathRules;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.constant.VersionConstant;
import com.korur.oba.apps.kaya.version.constant.service.VersionServiceLogConstant;
import com.korur.oba.apps.kaya.version.dto.embedded.DeliveredFile;
import com.korur.oba.apps.kaya.version.dto.embedded.ItemSource;
import com.korur.oba.apps.kaya.version.dto.embedded.RebaseConflict;
import com.korur.oba.apps.kaya.version.dto.request.DraftCreateRequest;
import com.korur.oba.apps.kaya.version.dto.request.DraftFileSaveRequest;
import com.korur.oba.apps.kaya.version.dto.request.DraftRebaseRequest;
import com.korur.oba.apps.kaya.version.dto.request.ItemOverrideRequest;
import com.korur.oba.apps.kaya.version.dto.request.ProposalFileRequest;
import com.korur.oba.apps.kaya.version.dto.response.DraftFileSaveResponse;
import com.korur.oba.apps.kaya.version.dto.response.ItemOverrideResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.entity.ItemOverride;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionSource;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;
import com.korur.oba.apps.kaya.version.helper.ExecutableChangeDetector;
import com.korur.oba.apps.kaya.version.helper.ThreeWayMerge;
import com.korur.oba.apps.kaya.version.mapper.ItemOverrideMapper;
import com.korur.oba.apps.kaya.version.mapper.VersionMapper;
import com.korur.oba.apps.kaya.version.repository.ItemOverrideRepository;
import com.korur.oba.apps.kaya.version.repository.VersionRepository;
import com.korur.oba.apps.kaya.version.service.ItemOverrideQueryService;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;
import com.korur.oba.apps.kaya.version.service.VersionService;

/**
 * Writes versions through render and parse: a new version receives the files its source renders,
 * changed in memory where the write asks for it, and {@link RenderService} stores them and checks
 * every file. Writers of one pack take its advisory lock first, so version numbers and
 * the current-version check are never read by two writers at once; a writer of one draft also
 * locks the draft row, always before the owner lock.
 */
@Service
@RequiredArgsConstructor
public class VersionServiceImpl implements VersionService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(VersionServiceImpl.class);

    private final VersionRepository versionRepository;
    private final VersionQueryService versionQueryService;
    private final PackQueryService packQueryService;
    private final PackService packService;
    private final RenderService renderService;
    private final ValidationQueryService validationQueryService;
    private final ReleaseQueryService releaseQueryService;
    private final ExecutableChangeDetector executableChangeDetector;
    private final VersionMapper versionMapper;
    private final ItemOverrideRepository itemOverrideRepository;
    private final ItemOverrideQueryService itemOverrideQueryService;
    private final ItemOverrideMapper itemOverrideMapper;
    private final Clock clock;
    private final LoggingService loggingService;
    private final EntityManager entityManager;

    /**
     * The pack a version belongs to, as far as this domain needs it. Its files are stored at
     * repository paths, so render and parse take no folder for it.
     */
    private record Owner(UUID packId, String name, UUID currentVersionId) {

        static Owner of(Pack pack) {
            return new Owner(pack.getId(), pack.getName(), pack.getCurrentVersionId());
        }

        UUID id() {
            return packId;
        }
    }

    /** What makes two override records the same for the unchanged check of a submit. */
    private record OverrideState(String path, UUID basePackId, UUID baseVersionId) {

        static OverrideState of(ItemOverride override) {
            return new OverrideState(override.getPath(), override.getBasePackId(), override.getBaseVersionId());
        }
    }

    /**
     * The state of a path, equal exactly when the files are the same: content hash, mode, link
     * target and the overridden path, since the last changes what a repository is delivered.
     */
    private record FileState(String sha256, FileMode mode, String symlinkTarget, String overridesPath) {

        static FileState of(RenderedFile file) {
            return new FileState(file.sha256(), file.mode(), file.symlinkTarget(), file.overridesPath());
        }

        static FileState of(SourceFile file) {
            return new FileState(file.content() == null ? null : sha256Of(file.content()), file.mode(), file.symlinkTarget(),
                    file.overridesPath());
        }
    }

    /** A proposed file with its content decoded once, for the staleness check and the write. */
    private record ProposedFile(ProposalFileRequest request, byte[] content) {

        boolean link() {
            return request.mode() == FileMode.SYMLINK;
        }

        DeliveredFile state() {
            if (request.removes()) {
                return null;
            }
            return new DeliveredFile(link() || content == null ? null : sha256Of(content), request.mode(),
                    link() ? request.symlinkTarget() : null);
        }
    }

    @Override
    @Transactional
    public VersionResponse createDraft(User user, UUID organizationId, DraftCreateRequest request) {
        Owner owner = lockOwner(organizationId, request.packId());
        Version current = currentOf(organizationId, owner);
        Version draft = newVersion(organizationId, owner, current, user.getId(), VersionSource.MANUAL);
        List<RenderedFile> currentFiles = versionQueryService.render(organizationId, current);
        store(organizationId, draft, owner, sources(currentFiles), currentFiles);
        copyOverrides(organizationId, current, draft);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.DRAFT_CREATED,
                draft.getId(), draft.getNumber(), owner.id(), owner.currentVersionId(), user.getId());
        return versionMapper.toResponse(draft);
    }

    @Override
    @Transactional
    public DraftFileSaveResponse saveFile(User user, UUID organizationId, UUID draftId, DraftFileSaveRequest request) {
        Version draft = ownDraft(user, organizationId, draftId);
        Owner owner = ownerOf(organizationId, draft);
        FileMode mode = Boolean.TRUE.equals(request.executable()) ? FileMode.EXECUTABLE : FileMode.FILE;
        if (mode == FileMode.EXECUTABLE) {
            requireExecutablePath(draftId, owner, request.path());
        }
        byte[] content = decode(request.content(), request.encoding());
        requireUnlockedAndScoped(organizationId, owner, request.path(), mode, content);
        requireNoNewOverrideMarker(draftId, List.of(new SourceFile(request.path(), mode, null, null, request.overridesPath())),
                markersOf(versionQueryService.render(organizationId, draft)));

        renderService.parseOneChange(organizationId, draftId,
                new SourceFile(request.path(), mode, null, content, request.overridesPath()),
                versionQueryService.render(organizationId, draft.getBaseVersion()));
        List<RenderedFile> after = versionQueryService.render(organizationId, draft);
        refreshExecutableChange(organizationId, draft, after);
        recordOverride(organizationId, draft, owner, PathRules.itemOf(request.path()));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.FILE_SAVED, request.path(), mode, draftId);
        RenderedFile saved = after.stream()
                .filter(file -> file.path().equals(request.path()))
                .findFirst()
                .orElseThrow(() -> fileNotFound(draftId, request.path()));
        return versionMapper.toSaveResponse(saved);
    }

    /**
     * Refuses a saved file that replaces an item an ancestor locks, or a rule that is not scoped to
     * every folder the pack is mounted at. Both are checked again at submit and approval, where
     * the compositions are read; refusing here tells the author before the draft holds the file.
     */
    private void requireUnlockedAndScoped(UUID organizationId, Owner owner, String path, FileMode mode, byte[] content) {
        List<Violation> violations = new ArrayList<>(releaseQueryService.getLockViolations(organizationId, owner.packId(),
                List.of(path)));
        violations.addAll(releaseQueryService.getRuleScopeViolations(organizationId, owner.packId(), path, mode, content));
        validationQueryService.requireValid(violations);
    }

    /**
     * Only a hook and a skill script carry the executable bit; any other file of a skill directory
     * that is saved executable is stored as a script. A path no kind delivers is left to the parse,
     * which refuses it as NOT_DELIVERABLE.
     */
    private void requireExecutablePath(UUID draftId, Owner owner, String path) {
        Optional<EntityKind> kind = DeliveryPaths.classify(path, null)
                .map(placed -> placed.entityKind(FileMode.EXECUTABLE));
        if (kind.isPresent() && kind.get() != EntityKind.HOOK && kind.get() != EntityKind.SCRIPT) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.EXECUTABLE_NOT_ALLOWED, path, draftId,
                    kind.get());
            throw RenderRefusals.invalidFile(path, VersionConstant.EXECUTABLE_NOT_ALLOWED_REASON);
        }
    }

    @Override
    @Transactional
    public void deleteFile(User user, UUID organizationId, UUID draftId, String path) {
        Version draft = ownDraft(user, organizationId, draftId);
        boolean present = versionQueryService.render(organizationId, draft).stream().anyMatch(file -> file.path().equals(path));
        if (!present) {
            throw fileNotFound(draftId, path);
        }
        renderService.parseOne(organizationId, draftId, SourceFile.removal(path));
        List<RenderedFile> after = versionQueryService.render(organizationId, draft);
        refreshExecutableChange(organizationId, draft, after);
        removeOverrideIfGone(draft, PathRules.itemOf(path), after);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.FILE_DELETED, path, draftId);
    }

    @Override
    @Transactional
    public void deleteDraft(User user, UUID organizationId, UUID draftId) {
        Version draft = ownDraft(user, organizationId, draftId);
        // A rejected draft carries the reviewer's decision and comments, which would go with the
        // version (ON DELETE CASCADE). The record of a review outlives the proposer's change of mind.
        if (draft.getDecidedAt() != null) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.DRAFT_DELETE_REVIEWED,
                    draftId, draft.getDecidedAt());
            throw new ConflictException(ExceptionDomainKayaError.VERSION_DRAFT_REVIEWED,
                    Map.of(VersionConstant.FIELD_VERSION_ID, draftId.toString()));
        }
        UUID ownerId = draft.getPackId();
        // The kind rows go with the version (ON DELETE CASCADE).
        versionRepository.delete(draft);
        versionRepository.flush();
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.DRAFT_DELETED, draftId, ownerId);
    }

    @Override
    @Transactional
    public VersionResponse rebase(User user, UUID organizationId, UUID draftId, DraftRebaseRequest request) {
        Version draft = ownDraft(user, organizationId, draftId);
        Owner owner = lockOwner(organizationId, draft.getPackId());
        Version base = draft.getBaseVersion();
        UUID baseId = base == null ? null : base.getId();
        if (Objects.equals(baseId, owner.currentVersionId())) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.REBASE_NOT_NEEDED, draftId, baseId);
            return versionMapper.toResponse(draft);
        }

        Set<String> keepDraft = pathsOf(request == null ? null : request.keepDraft());
        Set<String> takeCurrent = pathsOf(request == null ? null : request.takeCurrent());
        Set<String> twice = new TreeSet<>(keepDraft);
        twice.retainAll(takeCurrent);
        if (!twice.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.REBASE_RESOLVED_TWICE, draftId, twice.size());
            throw new ValidationException(ExceptionValidationError.VALIDATION, Map.of(VersionConstant.FIELD_PATHS, List.copyOf(twice)));
        }

        Version current = currentOf(organizationId, owner);
        Map<String, RenderedFile> draftFiles = byPath(versionQueryService.render(organizationId, draft));
        Map<String, RenderedFile> currentFiles = byPath(versionQueryService.render(organizationId, current));
        Map<String, FileState> baseStates = statesOf(byPath(versionQueryService.render(organizationId, base)));
        Map<String, FileState> draftStates = statesOf(draftFiles);
        Map<String, FileState> currentStates = statesOf(currentFiles);
        ThreeWayMerge.Outcome outcome = ThreeWayMerge.merge(baseStates, draftStates, currentStates);

        List<RebaseConflict> unresolved = outcome.conflicts().stream()
                .filter(path -> !keepDraft.contains(path) && !takeCurrent.contains(path))
                .map(path -> versionMapper.toConflict(path,
                        ThreeWayMerge.change(baseStates.get(path), draftStates.get(path)),
                        ThreeWayMerge.change(baseStates.get(path), currentStates.get(path))))
                .toList();
        if (!unresolved.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.REBASE_CONFLICTS,
                    draftId, owner.currentVersionId(), unresolved.size());
            throw rebaseRequired(owner.currentVersionId(), unresolved);
        }

        Set<String> taken = new TreeSet<>(outcome.takeCurrent());
        outcome.conflicts().stream().filter(takeCurrent::contains).forEach(taken::add);
        Map<String, SourceFile> merged = new TreeMap<>(sourcesByPath(draftFiles.values()));
        for (String path : taken) {
            RenderedFile currentFile = currentFiles.get(path);
            if (currentFile == null) {
                merged.remove(path);
            } else {
                merged.put(path, currentFile.toSource());
            }
        }
        // The whole merged tree in one parse, so a skill taken whole never passes through half a skill.
        renderService.parseChange(organizationId, draftId, List.copyOf(merged.values()),
                List.copyOf(currentFiles.values()));
        draft.setBaseVersion(current);
        refreshExecutableChange(organizationId, draft, versionQueryService.render(organizationId, draft));
        versionRepository.saveAndFlush(draft);
        takeCurrentOverrides(organizationId, current, draft, taken);
        syncOverrides(organizationId, draft, owner);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.REBASED,
                draftId, baseId, owner.currentVersionId(), taken.size());
        return versionMapper.toResponse(draft);
    }

    @Override
    @Transactional
    public VersionResponse restore(User user, UUID organizationId, UUID versionId) {
        Version restored = versionQueryService.getEntityById(organizationId, versionId);
        if (restored.getStatus() != VersionStatus.APPROVED && restored.getStatus() != VersionStatus.SUPERSEDED) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.RESTORE_NOT_RELEASED,
                    versionId, restored.getStatus());
            throw new ConflictException(ExceptionDomainKayaError.VERSION_NOT_RELEASED,
                    Map.of(VersionConstant.FIELD_VERSION_ID, versionId.toString(),
                            VersionConstant.FIELD_STATUS, restored.getStatus().name()));
        }
        Owner owner = lockOwner(organizationId, restored.getPackId());
        if (versionId.equals(owner.currentVersionId())) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.RESTORE_CURRENT, versionId, owner.id());
            throw new ConflictException(ExceptionDomainKayaError.VERSION_DRAFT_UNCHANGED,
                    Map.of(VersionConstant.FIELD_VERSION_ID, versionId.toString()));
        }
        Version current = currentOf(organizationId, owner);
        Version draft = newVersion(organizationId, owner, current, user.getId(), VersionSource.ROLLBACK);
        List<RenderedFile> restoredFiles = versionQueryService.render(organizationId, restored);
        List<RenderedFile> currentFiles = versionQueryService.render(organizationId, current);
        store(organizationId, draft, owner, sources(restoredFiles), currentFiles);
        copyOverrides(organizationId, restored, draft);
        draft.setExecutableChange(executableChangeDetector.isExecutableChange(currentFiles, restoredFiles));
        versionRepository.saveAndFlush(draft);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.RESTORE_CREATED,
                draft.getId(), draft.getNumber(), versionId, owner.id(), user.getId());
        return versionMapper.toResponse(draft);
    }

    @Override
    @Transactional
    public Version submit(User user, UUID organizationId, UUID draftId, String reason) {
        Version draft = ownDraft(user, organizationId, draftId);
        Owner owner = lockOwner(organizationId, draft.getPackId());
        UUID baseId = draft.getBaseVersion() == null ? null : draft.getBaseVersion().getId();
        if (!Objects.equals(baseId, owner.currentVersionId())) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.SUBMIT_REBASE_NEEDED,
                    draftId, baseId, owner.currentVersionId());
            throw rebaseRequired(owner.currentVersionId(), List.of());
        }
        List<RenderedFile> before = versionQueryService.render(organizationId, draft.getBaseVersion());
        List<RenderedFile> after = versionQueryService.render(organizationId, draft);
        // A moved override base is a change of its own, reviewed like content.
        if (statesOf(byPath(after)).equals(statesOf(byPath(before)))
                && overrideStatesOf(organizationId, draftId).equals(overrideStatesOf(organizationId, baseId))) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.SUBMIT_UNCHANGED, draftId, baseId);
            throw new ConflictException(ExceptionDomainKayaError.VERSION_DRAFT_UNCHANGED,
                    Map.of(VersionConstant.FIELD_VERSION_ID, draftId.toString()));
        }
        requireNoneInReview(owner, VersionServiceLogConstant.SUBMIT_IN_REVIEW, draftId);

        draft.setExecutableChange(executableChangeDetector.isExecutableChange(before, after));
        draft.setStatus(VersionStatus.IN_REVIEW);
        draft.setReason(reason);
        draft.setSubmittedAt(clock.instant());
        // A rejected version submitted again waits for a new decision.
        draft.setDecidedAt(null);
        versionRepository.saveAndFlush(draft);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.SUBMITTED,
                draftId, owner.id(), draft.getExecutableChange());
        return draft;
    }

    @Override
    @Transactional
    public VersionResponse propose(UUID proposerId, UUID organizationId, UUID packId,
                                   Map<String, DeliveredFile> delivered, List<ProposalFileRequest> files, String reason) {
        List<ProposedFile> proposed = files.stream().map(VersionServiceImpl::toProposed).toList();
        Owner owner = lockOwner(organizationId, packId);
        requireNoneInReview(owner, VersionServiceLogConstant.PROPOSAL_IN_REVIEW, proposerId);

        Version current = currentOf(organizationId, owner);
        List<RenderedFile> currentList = versionQueryService.render(organizationId, current);
        Map<String, RenderedFile> currentFiles = byPath(currentList);
        List<RebaseConflict> conflicts = proposed.stream()
                .map(file -> staleConflict(file, delivered.get(file.request().path()),
                        deliveredOf(currentFiles.get(file.request().path()))))
                .filter(Objects::nonNull)
                .toList();
        if (!conflicts.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.PROPOSAL_STALE,
                    owner.id(), owner.currentVersionId(), conflicts.size(), proposerId);
            throw rebaseRequired(owner.currentVersionId(), conflicts);
        }

        Map<String, SourceFile> merged = applyProposed(currentFiles, proposed);
        if (statesOf(byPath(currentList)).equals(sourceStatesOf(merged))) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.PROPOSAL_UNCHANGED,
                    owner.id(), owner.currentVersionId(), proposerId);
            throw new ConflictException(ExceptionDomainKayaError.VERSION_DRAFT_UNCHANGED,
                    Map.of(VersionConstant.FIELD_CURRENT_VERSION_ID, String.valueOf(owner.currentVersionId())));
        }

        Version draft = newVersion(organizationId, owner, current, proposerId, VersionSource.CLI);
        store(organizationId, draft, owner, List.copyOf(merged.values()), currentList);
        copyOverrides(organizationId, current, draft);
        syncOverrides(organizationId, draft, owner);
        draft.setExecutableChange(executableChangeDetector.isExecutableChange(currentList,
                versionQueryService.render(organizationId, draft)));
        draft.setStatus(VersionStatus.IN_REVIEW);
        draft.setReason(reason);
        draft.setSubmittedAt(clock.instant());
        versionRepository.saveAndFlush(draft);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.PROPOSED, draft.getId(), draft.getNumber(),
                owner.id(), owner.currentVersionId(), proposed.size(), draft.getExecutableChange(), proposerId);
        return versionMapper.toResponse(draft);
    }

    @Override
    @Transactional
    public Optional<Version> seed(UUID proposerId, UUID organizationId, UUID packId, List<SourceFile> files,
                                  String reason) {
        Owner owner = lockOwner(organizationId, packId);
        Version current = currentOf(organizationId, owner);
        List<RenderedFile> currentList = versionQueryService.render(organizationId, current);
        Map<String, String> markers = markersOf(currentList);
        Map<String, SourceFile> seeded = new TreeMap<>();
        for (SourceFile file : files) {
            String marker = file.overridesPath() != null ? file.overridesPath() : markers.get(file.path());
            seeded.put(file.path(), new SourceFile(file.path(), file.mode(), file.symlinkTarget(), file.content(), marker));
        }
        if (statesOf(byPath(currentList)).equals(sourceStatesOf(seeded))) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.SEED_UNCHANGED, owner.id(),
                    owner.currentVersionId());
            return Optional.empty();
        }
        List<SourceFile> sources = List.copyOf(seeded.values());
        requireNoNewOverrideMarker(null, sources, markers);

        Version version = newVersion(organizationId, owner, current, proposerId, VersionSource.SEED);
        if (!sources.isEmpty()) {
            renderService.parseImported(organizationId, version.getId(), sources);
        }
        version.setExecutableChange(executableChangeDetector.isExecutableChange(currentList,
                versionQueryService.render(organizationId, version)));
        copyOverrides(organizationId, current, version);
        syncOverrides(organizationId, version, owner);
        if (current != null) {
            current.setStatus(VersionStatus.SUPERSEDED);
            versionRepository.save(current);
        }
        Instant now = clock.instant();
        version.setStatus(VersionStatus.APPROVED);
        version.setReason(reason);
        version.setSubmittedAt(now);
        version.setDecidedAt(now);
        versionRepository.saveAndFlush(version);
        packService.setCurrentVersion(organizationId, owner.packId(), version.getId());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.SEEDED, version.getId(), version.getNumber(),
                owner.id(), owner.currentVersionId(), sources.size(), version.getExecutableChange(), proposerId);
        return Optional.of(version);
    }

    @Override
    @Transactional
    public ItemOverrideResponse setOverrideBase(User user, UUID organizationId, UUID draftId, ItemOverrideRequest request) {
        Version draft = ownDraft(user, organizationId, draftId);
        Owner owner = ownerOf(organizationId, draft);
        String item = requireDraftItem(organizationId, draft, request.path());
        validationQueryService.requireValid(releaseQueryService.getLockViolations(organizationId, owner.packId(), List.of(item)));
        ItemSource source = itemOverrideQueryService.getInheritedSources(organizationId, owner.packId()).get(item);
        if (source == null || !source.versionId().equals(request.baseVersionId())) {
            UUID currentId = source == null ? null : source.versionId();
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.OVERRIDE_BASE_STALE,
                    request.baseVersionId(), item, draftId, currentId);
            Map<String, Object> errorValues = new HashMap<>();
            errorValues.put(VersionConstant.FIELD_PATH, item);
            errorValues.put(VersionConstant.FIELD_BASE_VERSION_ID, request.baseVersionId().toString());
            errorValues.put(VersionConstant.FIELD_PARENT_VERSION_ID, currentId == null ? null : currentId.toString());
            throw new ConflictException(ExceptionDomainKayaError.OVERRIDE_BASE_STALE, errorValues);
        }
        ItemOverride override = itemOverrideRepository.findByVersionIdAndPath(draftId, item)
                .orElseGet(() -> newOverride(organizationId, draftId, item, source));
        override.setBasePackId(source.packId());
        override.setBaseVersionId(source.versionId());
        ItemOverride saved = itemOverrideRepository.saveAndFlush(override);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.OVERRIDE_BASE_SET, item, draftId,
                source.packId(), source.versionId());
        return itemOverrideMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void clearOverride(User user, UUID organizationId, UUID draftId, String path) {
        Version draft = ownDraft(user, organizationId, draftId);
        Owner owner = ownerOf(organizationId, draft);
        String item = PathRules.itemOf(path);
        ItemOverride override = itemOverrideRepository.findByVersionIdAndPath(draftId, item).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.OVERRIDE_NOT_FOUND, draftId, item);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(VersionConstant.FIELD_VERSION_ID, draftId.toString(), VersionConstant.FIELD_PATH, item));
        });
        // While an ancestor still delivers the item the record is what the override is measured
        // against; the way back to the inherited item is deleting the draft's file.
        if (itemOverrideQueryService.getInheritedSources(organizationId, owner.packId()).containsKey(item)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.OVERRIDE_STILL_INHERITED, item, draftId);
            throw new ValidationException(ExceptionValidationError.VALIDATION,
                    Map.of(VersionConstant.FIELD_PATH, VersionConstant.ANCESTOR_STILL_DELIVERS_MESSAGE));
        }
        itemOverrideRepository.delete(override);
        itemOverrideRepository.flush();
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.OVERRIDE_CLEARED, item, draftId);
    }

    @Override
    @Transactional
    public Version approve(UUID organizationId, UUID versionId) {
        Version version = lockedVersion(organizationId, versionId);
        requireInReview(version);
        Owner owner = lockOwner(organizationId, version.getPackId());
        UUID baseId = version.getBaseVersion() == null ? null : version.getBaseVersion().getId();
        UUID currentId = owner.currentVersionId();
        if (!Objects.equals(baseId, currentId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.APPROVE_BASE_NOT_CURRENT,
                    versionId, baseId, currentId);
            throw rebaseRequired(currentId, List.of());
        }
        Version current = currentOf(organizationId, owner);
        if (current != null) {
            current.setStatus(VersionStatus.SUPERSEDED);
            versionRepository.save(current);
        }
        version.setStatus(VersionStatus.APPROVED);
        version.setDecidedAt(clock.instant());
        versionRepository.saveAndFlush(version);
        packService.setCurrentVersion(organizationId, owner.packId(), versionId);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.APPROVED, versionId, owner.id(), currentId);
        return version;
    }

    @Override
    @Transactional
    public Version reject(UUID organizationId, UUID versionId) {
        Version version = lockedVersion(organizationId, versionId);
        requireInReview(version);
        version.setStatus(VersionStatus.DRAFT);
        version.setSubmittedAt(null);
        version.setDecidedAt(clock.instant());
        versionRepository.saveAndFlush(version);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.REJECTED,
                versionId, version.getPackId(), version.getProposedBy());
        return version;
    }

    /**
     * Takes the advisory lock of the named pack and reads it again afterwards, so its
     * current-version pointer is the committed one under the lock.
     *
     * A caller may have loaded the pack earlier in the transaction, as a seed and a
     * delivery proposal do to find the owner, and a lookup then hands back that instance as it was
     * before the lock. Without the refresh an approval that committed meanwhile stays invisible:
     * the base check passes on the old pointer and a second approval supersedes the wrong version.
     */
    private Owner lockOwner(UUID organizationId, UUID packId) {
        versionRepository.lockOwner(packId.toString());
        Pack pack = packQueryService.getEntityById(organizationId, packId);
        entityManager.refresh(pack);
        return Owner.of(pack);
    }

    /** The owner of a version, read without its lock, for a write that does not move the owner. */
    private Owner ownerOf(UUID organizationId, Version version) {
        return Owner.of(packQueryService.getEntityById(organizationId, version.getPackId()));
    }

    private Version currentOf(UUID organizationId, Owner owner) {
        return owner.currentVersionId() == null ? null : versionQueryService.getEntityById(organizationId, owner.currentVersionId());
    }

    private Version newVersion(UUID organizationId, Owner owner, Version base, UUID proposerId, VersionSource source) {
        return versionRepository.saveAndFlush(Version.builder()
                .organizationId(organizationId)
                .packId(owner.packId())
                .number(versionRepository.findMaxNumber(owner.id()) + 1)
                .status(VersionStatus.DRAFT)
                .baseVersion(base)
                .proposedBy(proposerId)
                .source(source)
                .executableChange(false)
                .build());
    }

    /**
     * Parses a whole file tree into a new version as a change of the base's files; a version without
     * files has nothing to parse.
     */
    private void store(UUID organizationId, Version version, Owner owner, List<SourceFile> files, List<RenderedFile> base) {
        if (!files.isEmpty()) {
            renderService.parseChange(organizationId, version.getId(), files, base);
        }
    }

    private void refreshExecutableChange(UUID organizationId, Version draft, List<RenderedFile> after) {
        draft.setExecutableChange(executableChangeDetector.isExecutableChange(
                versionQueryService.render(organizationId, draft.getBaseVersion()), after));
    }

    /**
     * Loads and locks a draft for a write by its proposer. Another organization's version answers
     * the same 404 as an unknown id; another member's draft answers 403, because every member may
     * read it; a version past DRAFT answers 409.
     */
    private Version ownDraft(User user, UUID organizationId, UUID draftId) {
        Version draft = lockedVersion(organizationId, draftId);
        if (!user.getId().equals(draft.getProposedBy())) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.DRAFT_NOT_OWN,
                    user.getId(), draft.getProposedBy(), draftId);
            throw new ForbiddenException(ExceptionSecurityError.FORBIDDEN,
                    Map.of(VersionConstant.FIELD_VERSION_ID, draftId.toString(),
                            VersionConstant.FIELD_PROPOSED_BY, draft.getProposedBy().toString()));
        }
        if (draft.getStatus() != VersionStatus.DRAFT) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.DRAFT_NOT_DRAFT, draftId, draft.getStatus());
            throw new ConflictException(ExceptionDomainKayaError.VERSION_NOT_A_DRAFT,
                    Map.of(VersionConstant.FIELD_VERSION_ID, draftId.toString(),
                            VersionConstant.FIELD_STATUS, draft.getStatus().name()));
        }
        return draft;
    }

    /**
     * Locks a version row and reads it again under the lock, so a status check afterwards sees the
     * committed state.
     *
     * A review reads the version before it calls in here, and a locking query hands back that
     * instance as it was then. Without the refresh, an approval waiting on a rejection that
     * committed meanwhile would still see IN_REVIEW and approve and release the rejected version,
     * and a rejection waiting on an approval would turn the new current version back into a draft.
     */
    private Version lockedVersion(UUID organizationId, UUID versionId) {
        Version version = versionRepository.findWithLockById(versionId)
                .filter(found -> organizationId.equals(found.getOrganizationId()))
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.VERSION_NOT_FOUND,
                            versionId, organizationId);
                    return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                            Map.of(VersionConstant.FIELD_VERSION_ID, versionId.toString()));
                });
        entityManager.refresh(version);
        return version;
    }

    private void requireInReview(Version version) {
        if (version.getStatus() == VersionStatus.IN_REVIEW) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.NOT_IN_REVIEW, version.getId(), version.getStatus());
        throw new ConflictException(ExceptionDomainKayaError.REVIEW_VERSION_NOT_IN_REVIEW,
                Map.of(VersionConstant.FIELD_VERSION_ID, version.getId().toString(),
                        VersionConstant.FIELD_STATUS, version.getStatus().name()));
    }

    private void requireNoneInReview(Owner owner, String logMessage, UUID subject) {
        Optional<Version> inReview = versionRepository.findFirstByOwnerAndStatus(owner.id(), VersionStatus.IN_REVIEW);
        if (inReview.isPresent()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, logMessage, owner.id(), inReview.get().getId(), subject);
            throw new ConflictException(ExceptionDomainKayaError.VERSION_IN_REVIEW,
                    Map.of(VersionConstant.FIELD_IN_REVIEW_VERSION_ID, inReview.get().getId().toString()));
        }
    }

    /**
     * Refuses a written file that names an inherited path it overrides when the file does not
     * already carry that marker: a pack's own write may not start an override, while the markers its
     * files already carry are kept as they are, so a pack that took over a folder's config keeps
     * delivering its narrowed rules and docs.
     */
    private void requireNoNewOverrideMarker(UUID draftId, List<SourceFile> files, Map<String, String> carried) {
        List<Violation> violations = files.stream()
                .filter(file -> file.overridesPath() != null && !file.overridesPath().equals(carried.get(file.path())))
                .map(file -> Violation.ofPath(file.path(), ViolationRule.OVERRIDE_NOT_ALLOWED,
                        VersionConstant.OVERRIDE_NOT_OWN_MESSAGE))
                .toList();
        if (!violations.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.FILE_REFUSED,
                    violations.get(0).path(), draftId, violations.size());
        }
        validationQueryService.requireValid(violations);
    }

    /** The overridden path of each file that carries one, by the file's path. */
    private static Map<String, String> markersOf(List<RenderedFile> files) {
        Map<String, String> markers = new HashMap<>();
        files.stream()
                .filter(file -> file.overridesPath() != null)
                .forEach(file -> markers.put(file.path(), file.overridesPath()));
        return markers;
    }

    /**
     * The item a path of a draft names, which the draft must hold: a path inside a skill names its
     * skill directory.
     */
    private String requireDraftItem(UUID organizationId, Version draft, String path) {
        String item = PathRules.itemOf(path);
        if (itemOverrideQueryService.filesOf(versionQueryService.render(organizationId, draft), item).isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.OVERRIDE_ITEM_NOT_FOUND, draft.getId(), item);
            throw new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(VersionConstant.FIELD_VERSION_ID, draft.getId().toString(), VersionConstant.FIELD_PATH, item));
        }
        return item;
    }

    /**
     * Records that a saved item replaces an item an ancestor delivers, based on the nearest such
     * ancestor's current version, unless the version already records it: an existing record keeps
     * its base, which only an explicit decision on the parent's change moves.
     */
    private void recordOverride(UUID organizationId, Version version, Owner owner, String item) {
        if (itemOverrideRepository.findByVersionIdAndPath(version.getId(), item).isPresent()) {
            return;
        }
        ItemSource source = itemOverrideQueryService.getInheritedSources(organizationId, owner.packId()).get(item);
        if (source != null) {
            itemOverrideRepository.saveAndFlush(newOverride(organizationId, version.getId(), item, source));
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.OVERRIDE_RECORDED, item,
                    version.getId(), source.packId(), source.versionId());
        }
    }

    /** Drops the record of an item the version no longer holds, so the ancestor's item is inherited again. */
    private void removeOverrideIfGone(Version version, String item, List<RenderedFile> files) {
        if (!itemOverrideQueryService.filesOf(files, item).isEmpty()) {
            return;
        }
        itemOverrideRepository.findByVersionIdAndPath(version.getId(), item).ifPresent(override -> {
            itemOverrideRepository.delete(override);
            itemOverrideRepository.flush();
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.OVERRIDE_REMOVED, item, version.getId());
        });
    }

    /**
     * Brings a version's records in line with its files after a whole-tree write: a record of an
     * item the version no longer holds goes, and an item an ancestor delivers without a record gets
     * one on the nearest ancestor's current version. Existing records keep their base.
     */
    private void syncOverrides(UUID organizationId, Version version, Owner owner) {
        Set<String> items = versionQueryService.render(organizationId, version).stream()
                .map(file -> PathRules.itemOf(file.path()))
                .collect(Collectors.toCollection(TreeSet::new));
        Map<String, ItemOverride> recorded = new HashMap<>();
        for (ItemOverride override : itemOverrideQueryService.getEntitiesByVersionId(organizationId, version.getId())) {
            if (items.contains(override.getPath())) {
                recorded.put(override.getPath(), override);
            } else {
                itemOverrideRepository.delete(override);
                loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.OVERRIDE_REMOVED,
                        override.getPath(), version.getId());
            }
        }
        Map<String, ItemSource> inherited = itemOverrideQueryService.getInheritedSources(organizationId, owner.packId());
        for (String item : items) {
            ItemSource source = inherited.get(item);
            if (source != null && !recorded.containsKey(item)) {
                itemOverrideRepository.save(newOverride(organizationId, version.getId(), item, source));
                loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.OVERRIDE_RECORDED, item,
                        version.getId(), source.packId(), source.versionId());
            }
        }
        itemOverrideRepository.flush();
    }

    /** Copies the records of one version into a new version made from its files. */
    private void copyOverrides(UUID organizationId, Version from, Version to) {
        if (from == null) {
            return;
        }
        List<ItemOverride> copies = itemOverrideQueryService.getEntitiesByVersionId(organizationId, from.getId()).stream()
                .map(override -> copyOf(override, to.getId()))
                .toList();
        if (copies.isEmpty()) {
            return;
        }
        itemOverrideRepository.saveAllAndFlush(copies);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.OVERRIDES_COPIED, copies.size(),
                from.getId(), to.getId());
    }

    /**
     * After a rebase, each item taken from the current version takes that version's record too, so
     * the draft does not keep a base for content it no longer has.
     */
    private void takeCurrentOverrides(UUID organizationId, Version current, Version draft, Set<String> takenPaths) {
        Map<String, ItemOverride> theirs = overridesByPath(organizationId, current == null ? null : current.getId());
        Map<String, ItemOverride> mine = overridesByPath(organizationId, draft.getId());
        Set<String> items = takenPaths.stream().map(PathRules::itemOf).collect(Collectors.toCollection(TreeSet::new));
        for (String item : items) {
            ItemOverride taken = theirs.get(item);
            ItemOverride own = mine.get(item);
            if (taken == null) {
                if (own != null) {
                    itemOverrideRepository.delete(own);
                }
            } else if (own == null) {
                itemOverrideRepository.save(copyOf(taken, draft.getId()));
            } else {
                own.setBasePackId(taken.getBasePackId());
                own.setBaseVersionId(taken.getBaseVersionId());
                itemOverrideRepository.save(own);
            }
        }
        itemOverrideRepository.flush();
    }

    private Map<String, ItemOverride> overridesByPath(UUID organizationId, UUID versionId) {
        if (versionId == null) {
            return Map.of();
        }
        return itemOverrideQueryService.getEntitiesByVersionId(organizationId, versionId).stream()
                .collect(Collectors.toMap(ItemOverride::getPath, Function.identity()));
    }

    private Set<OverrideState> overrideStatesOf(UUID organizationId, UUID versionId) {
        return overridesByPath(organizationId, versionId).values().stream().map(OverrideState::of).collect(Collectors.toSet());
    }

    private static ItemOverride newOverride(UUID organizationId, UUID versionId, String item, ItemSource source) {
        return ItemOverride.builder()
                .organizationId(organizationId)
                .versionId(versionId)
                .path(item)
                .basePackId(source.packId())
                .baseVersionId(source.versionId())
                .build();
    }

    private static ItemOverride copyOf(ItemOverride override, UUID versionId) {
        return ItemOverride.builder()
                .organizationId(override.getOrganizationId())
                .versionId(versionId)
                .path(override.getPath())
                .basePackId(override.getBasePackId())
                .baseVersionId(override.getBaseVersionId())
                .build();
    }

    private static ConflictException rebaseRequired(UUID currentVersionId, List<RebaseConflict> conflicts) {
        Map<String, Object> errorValues = new HashMap<>();
        errorValues.put(VersionConstant.FIELD_CURRENT_VERSION_ID, currentVersionId == null ? null : currentVersionId.toString());
        errorValues.put(VersionConstant.FIELD_CONFLICTS, conflicts);
        return new ConflictException(ExceptionDomainKayaError.VERSION_REBASE_REQUIRED, errorValues);
    }

    private NotFoundException fileNotFound(UUID draftId, String path) {
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.DRAFT_FILE_NOT_FOUND, draftId, path);
        return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                Map.of(VersionConstant.FIELD_VERSION_ID, draftId.toString(), VersionConstant.FIELD_PATH, path));
    }

    /** The content of a FILE or EXECUTABLE in the request's encoding; null when none is sent. */
    private static byte[] decode(String content, FileEncoding encoding) {
        if (content == null) {
            return null;
        }
        if (encoding != FileEncoding.BASE64) {
            return content.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ExceptionValidationError.VALIDATION,
                    Map.of(VersionConstant.FIELD_CONTENT, VersionConstant.CONTENT_NOT_BASE64_MESSAGE));
        }
    }

    private static ProposedFile toProposed(ProposalFileRequest request) {
        if (!request.removes() && request.mode() == null) {
            throw new ValidationException(ExceptionValidationError.VALIDATION,
                    Map.of(VersionConstant.FIELD_PATH, request.path(),
                            VersionConstant.FIELD_MODE, VersionConstant.MODE_REQUIRED_MESSAGE));
        }
        boolean link = request.mode() == FileMode.SYMLINK;
        return new ProposedFile(request, request.removes() || link ? null : decode(request.content(), request.encoding()));
    }

    /**
     * The tree of a base with the proposed files applied: a removal drops its path, any other file
     * replaces it and keeps the overridden path of the base file at that path.
     */
    private static Map<String, SourceFile> applyProposed(Map<String, RenderedFile> baseFiles, List<ProposedFile> proposed) {
        Map<String, SourceFile> merged = new TreeMap<>(sourcesByPath(baseFiles.values()));
        for (ProposedFile file : proposed) {
            ProposalFileRequest request = file.request();
            if (request.removes()) {
                merged.remove(request.path());
                continue;
            }
            RenderedFile existing = baseFiles.get(request.path());
            merged.put(request.path(), new SourceFile(request.path(), request.mode(), file.link() ? request.symlinkTarget() : null,
                    file.content(), existing == null ? null : existing.overridesPath()));
        }
        return merged;
    }

    /**
     * The conflict of a proposed path whose state in the current version is no longer the one the
     * release delivered, unless the proposal already matches the current state. Null otherwise.
     */
    private RebaseConflict staleConflict(ProposedFile file, DeliveredFile delivered, DeliveredFile current) {
        DeliveredFile proposed = file.state();
        if (Objects.equals(delivered, current) || Objects.equals(proposed, current)) {
            return null;
        }
        return versionMapper.toConflict(file.request().path(), ThreeWayMerge.change(delivered, proposed),
                ThreeWayMerge.change(delivered, current));
    }

    private DeliveredFile deliveredOf(RenderedFile file) {
        return file == null ? null : versionMapper.toDeliveredFile(file);
    }

    private static List<SourceFile> sources(List<RenderedFile> files) {
        return files.stream().map(RenderedFile::toSource).toList();
    }

    private static Map<String, SourceFile> sourcesByPath(Iterable<RenderedFile> files) {
        Map<String, SourceFile> sources = new TreeMap<>();
        for (RenderedFile file : files) {
            sources.put(file.path(), file.toSource());
        }
        return sources;
    }

    private static Map<String, RenderedFile> byPath(List<RenderedFile> files) {
        return files.stream().collect(Collectors.toMap(RenderedFile::path, Function.identity()));
    }

    private static Map<String, FileState> statesOf(Map<String, RenderedFile> files) {
        return files.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> FileState.of(entry.getValue())));
    }

    private static Map<String, FileState> sourceStatesOf(Map<String, SourceFile> files) {
        return files.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> FileState.of(entry.getValue())));
    }

    private static Set<String> pathsOf(List<String> paths) {
        return paths == null ? Set.of() : new HashSet<>(paths);
    }

    private static String sha256Of(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(VersionConstant.SHA256_ALGORITHM).digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            // Every Java platform must provide SHA-256, so this means a broken runtime.
            throw new IllegalStateException(exception);
        }
    }
}
