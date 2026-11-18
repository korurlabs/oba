package com.korur.oba.apps.kaya.version.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 2. Third-party
import jakarta.persistence.EntityManager;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.pack.service.PackService;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileEncoding;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.service.RenderService;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.dto.request.DraftCreateRequest;
import com.korur.oba.apps.kaya.version.dto.request.DraftFileSaveRequest;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionSource;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;
import com.korur.oba.apps.kaya.version.helper.ExecutableChangeDetector;
import com.korur.oba.apps.kaya.version.mapper.ItemOverrideMapper;
import com.korur.oba.apps.kaya.version.mapper.VersionMapper;
import com.korur.oba.apps.kaya.version.repository.ItemOverrideRepository;
import com.korur.oba.apps.kaya.version.repository.VersionRepository;
import com.korur.oba.apps.kaya.version.service.ItemOverrideQueryService;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VersionServiceImplUnitTest {

    private static final String RULE = ".agents/rules/core.md";
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    @Mock
    private VersionRepository versionRepository;
    @Mock
    private VersionQueryService versionQueryService;
    @Mock
    private PackQueryService packQueryService;
    @Mock
    private PackService packService;
    @Mock
    private RenderService renderService;
    @Mock
    private ValidationQueryService validationQueryService;
    @Mock
    private ReleaseQueryService releaseQueryService;
    @Mock
    private ExecutableChangeDetector executableChangeDetector;
    @Mock
    private VersionMapper versionMapper;
    @Mock
    private ItemOverrideRepository itemOverrideRepository;
    @Mock
    private ItemOverrideQueryService itemOverrideQueryService;
    @Mock
    private ItemOverrideMapper itemOverrideMapper;
    @Mock
    private LoggingService loggingService;
    @Mock
    private EntityManager entityManager;

    private VersionServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID packId = UUID.randomUUID();
    private final UUID draftId = UUID.randomUUID();
    private User user;
    private Version current;

    @BeforeEach
    void setUp() {
        service = new VersionServiceImpl(versionRepository, versionQueryService, packQueryService, packService,
                renderService, validationQueryService, releaseQueryService, executableChangeDetector,
                versionMapper, itemOverrideRepository, itemOverrideQueryService, itemOverrideMapper,
                Clock.fixed(NOW, ZoneOffset.UTC), loggingService, entityManager);
        user = User.builder().id(UUID.randomUUID()).build();
        current = version(UUID.randomUUID(), 3, VersionStatus.APPROVED, null);
        when(versionRepository.saveAndFlush(any(Version.class))).thenAnswer(invocation -> {
            Version saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(draftId);
            }
            return saved;
        });
    }

    @Test
    void createDraft_packWithCurrentVersion_parsesTheRenderedCurrentVersionIntoTheNextNumber() {
        // Arrange
        givenPack(current.getId());
        List<RenderedFile> currentFiles = List.of(rendered(RULE, "# Core\n"));
        when(versionQueryService.getEntityById(organizationId, current.getId())).thenReturn(current);
        when(versionQueryService.render(organizationId, current)).thenReturn(currentFiles);
        when(versionRepository.findMaxNumber(packId)).thenReturn(3);

        // Act
        service.createDraft(user, organizationId, new DraftCreateRequest(packId));

        // Assert
        ArgumentCaptor<Version> draft = ArgumentCaptor.forClass(Version.class);
        verify(versionRepository).saveAndFlush(draft.capture());
        assertEquals(4, draft.getValue().getNumber());
        assertEquals(VersionStatus.DRAFT, draft.getValue().getStatus());
        assertEquals(VersionSource.MANUAL, draft.getValue().getSource());
        assertSame(current, draft.getValue().getBaseVersion());
        verify(renderService).parseChange(organizationId, draftId, List.of(currentFiles.get(0).toSource()), currentFiles);
    }

    @Test
    void saveFile_ownDraft_storesTheBytesAsAChangeOfTheBase() {
        // Arrange
        Version draft = givenOwnDraft();
        when(versionQueryService.render(organizationId, draft)).thenReturn(List.of(rendered(RULE, "# Edited\n")));

        // Act
        service.saveFile(user, organizationId, draftId, saveRequest("# Edited\n"));

        // Assert
        ArgumentCaptor<SourceFile> file = ArgumentCaptor.forClass(SourceFile.class);
        verify(renderService).parseOneChange(eq(organizationId), eq(draftId), file.capture(), anyList());
        assertEquals(RULE, file.getValue().path());
        assertEquals(FileMode.FILE, file.getValue().mode());
        assertArrayEquals("# Edited\n".getBytes(StandardCharsets.UTF_8), file.getValue().content());
    }

    @Test
    void saveFile_fileDoesNotParse_propagatesInvalidFileAndWritesNothingElse() {
        // Arrange
        Version draft = givenOwnDraft();
        ValidationException refusal = new ValidationException(ExceptionDomainKayaError.RENDER_INVALID_FILE,
                Map.of("path", RULE, "reason", "frontmatter is not valid YAML"));
        doThrow(refusal).when(renderService).parseOneChange(eq(organizationId), eq(draftId), any(SourceFile.class),
                anyList());

        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> service.saveFile(user, organizationId, draftId, saveRequest("---\nbad: [\n---\n")));

        // Assert
        assertSame(refusal, exception);
        assertNull(draft.getReason());
        verify(versionRepository, never()).saveAndFlush(any(Version.class));
        verify(renderService, never()).parseChange(any(), any(), anyList(), anyList());
    }

    @Test
    void saveFile_lockedOrUnscopedPath_isRefusedBeforeAnyParse() {
        // Arrange
        givenOwnDraft();
        Violation locked = new Violation(RULE, ViolationRule.OVERRIDE_LOCKED, "locked", null, List.of(packId));
        Violation unscoped = Violation.ofMountRule(null, RULE, packId, "projects/kagi", "unscoped");
        when(releaseQueryService.getLockViolations(organizationId, packId, List.of(RULE))).thenReturn(List.of(locked));
        when(releaseQueryService.getRuleScopeViolations(eq(organizationId), eq(packId), eq(RULE), eq(FileMode.FILE), any()))
                .thenReturn(List.of(unscoped));
        ValidationException refusal = new ValidationException(ExceptionDomainKayaError.MOUNT_RULE_UNSCOPED, Map.of());
        doThrow(refusal).when(validationQueryService).requireValid(List.of(locked, unscoped));

        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> service.saveFile(user, organizationId, draftId, saveRequest("# Edited\n")));

        // Assert
        assertSame(refusal, exception);
        verify(renderService, never()).parseOneChange(any(), any(), any(), anyList());
    }

    @Test
    void saveFile_draftOfAnotherMember_isForbiddenBeforeAnyParse() {
        // Arrange
        Version draft = version(draftId, 4, VersionStatus.DRAFT, current);
        draft.setProposedBy(UUID.randomUUID());
        when(versionRepository.findWithLockById(draftId)).thenReturn(Optional.of(draft));

        // Act and Assert
        assertThrows(ForbiddenException.class,
                () -> service.saveFile(user, organizationId, draftId, saveRequest("# Edited\n")));
        verify(renderService, never()).parseOneChange(any(), any(), any(), anyList());
    }

    @Test
    void approve_inReviewOnTheCurrentVersion_supersedesItAndMovesThePackPointer() {
        // Arrange
        givenPack(current.getId());
        Version inReview = version(draftId, 4, VersionStatus.IN_REVIEW, current);
        when(versionRepository.findWithLockById(draftId)).thenReturn(Optional.of(inReview));
        when(versionQueryService.getEntityById(organizationId, current.getId())).thenReturn(current);

        // Act
        Version approved = service.approve(organizationId, draftId);

        // Assert
        assertEquals(VersionStatus.APPROVED, approved.getStatus());
        assertEquals(NOW, approved.getDecidedAt());
        assertEquals(VersionStatus.SUPERSEDED, current.getStatus());
        verify(packService).setCurrentVersion(organizationId, packId, draftId);
    }

    private void givenPack(UUID currentVersionId) {
        Pack pack = Pack.builder().id(packId).organizationId(organizationId).name("org-pack")
                .currentVersionId(currentVersionId).build();
        when(packQueryService.getEntityById(organizationId, packId)).thenReturn(pack);
    }

    private Version givenOwnDraft() {
        givenPack(current.getId());
        Version draft = version(draftId, 4, VersionStatus.DRAFT, current);
        when(versionRepository.findWithLockById(draftId)).thenReturn(Optional.of(draft));
        return draft;
    }

    private Version version(UUID id, int number, VersionStatus status, Version base) {
        return Version.builder()
                .id(id)
                .organizationId(organizationId)
                .packId(packId)
                .number(number)
                .status(status)
                .baseVersion(base)
                .proposedBy(user.getId())
                .source(VersionSource.MANUAL)
                .build();
    }

    private static DraftFileSaveRequest saveRequest(String content) {
        return new DraftFileSaveRequest(RULE, FileEncoding.UTF_8, content, null, null);
    }

    private static RenderedFile rendered(String path, String text) {
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        return new RenderedFile(path, FileMode.FILE, null, content, "0".repeat(64), UUID.randomUUID(), null);
    }
}
