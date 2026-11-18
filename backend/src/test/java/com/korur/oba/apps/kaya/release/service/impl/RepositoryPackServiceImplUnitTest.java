package com.korur.oba.apps.kaya.release.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.pack.service.PackService;
import com.korur.oba.apps.kaya.release.dto.request.PackRequiredRequest;
import com.korur.oba.apps.kaya.release.dto.request.RepositoryPackItemRequest;
import com.korur.oba.apps.kaya.release.dto.request.RepositoryPackUpdateRequest;
import com.korur.oba.apps.kaya.release.dto.response.RepositoryPackEntryResponse;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.entity.RepositoryPack;
import com.korur.oba.apps.kaya.release.helper.ComposedFile;
import com.korur.oba.apps.kaya.release.helper.Composer;
import com.korur.oba.apps.kaya.release.helper.Composition;
import com.korur.oba.apps.kaya.release.helper.PackMount;
import com.korur.oba.apps.kaya.release.mapper.ReleaseMapper;
import com.korur.oba.apps.kaya.release.repository.ReleaseRepository;
import com.korur.oba.apps.kaya.release.repository.RepositoryPackRepository;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.release.service.ReleaseService;
import com.korur.oba.apps.kaya.release.service.RepositoryPackQueryService;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.entity.Version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryPackServiceImplUnitTest {

    @Mock
    private RepositoryPackRepository repositoryPackRepository;
    @Mock
    private ReleaseRepository releaseRepository;
    @Mock
    private Composer composer;
    @Mock
    private ReleaseService releaseService;
    @Mock
    private ValidationQueryService validationQueryService;
    @Mock
    private RepositoryQueryService repositoryQueryService;
    @Mock
    private PackQueryService packQueryService;
    @Mock
    private PackService packService;
    @Mock
    private ReleaseQueryService releaseQueryService;
    @Mock
    private RepositoryPackQueryService repositoryPackQueryService;
    @Mock
    private ReleaseMapper releaseMapper;
    @Mock
    private LoggingService loggingService;

    private RepositoryPackServiceImpl service;
    private UUID organizationId;
    private UUID repositoryId;
    private User actor;
    private Pack low;
    private Pack high;

    @BeforeEach
    void setUp() {
        service = new RepositoryPackServiceImpl(repositoryPackRepository, releaseRepository, composer, releaseService,
                releaseQueryService, repositoryPackQueryService, validationQueryService, repositoryQueryService,
                packQueryService, packService, releaseMapper, loggingService);
        organizationId = UUID.randomUUID();
        repositoryId = UUID.randomUUID();
        actor = User.builder().id(UUID.randomUUID()).build();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID lowId = first.compareTo(second) < 0 ? first : second;
        UUID highId = lowId == first ? second : first;
        low = Pack.builder().id(lowId).organizationId(organizationId).name("low").currentVersionId(UUID.randomUUID()).build();
        high = Pack.builder().id(highId).organizationId(organizationId).name("high").currentVersionId(UUID.randomUUID()).build();
        lenient().doThrow(new ValidationException(ExceptionValidationError.VALIDATION))
                .when(validationQueryService).requireValid(argThat(violations -> violations != null && !violations.isEmpty()));
        lenient().when(packQueryService.getEntitiesByIds(eq(organizationId), any())).thenReturn(List.of(low, high));
        // Without required packs the composed order is the listed order.
        lenient().when(repositoryPackQueryService.getComposedPacks(eq(organizationId), any())).thenAnswer(invocation -> {
            List<PackMount> mounts = invocation.getArgument(1);
            return mounts.stream().map(mount -> entry(mount.packId().equals(low.getId()) ? low : high, mount.rootPath())).toList();
        });
    }

    private void storedOrder(Pack... packs) {
        List<RepositoryPack> rows = new ArrayList<>();
        for (int position = 0; position < packs.length; position++) {
            rows.add(RepositoryPack.builder().repositoryId(repositoryId).packId(packs[position].getId()).position(position).build());
        }
        when(repositoryPackRepository.findByRepositoryIdOrderByPositionAsc(repositoryId)).thenReturn(rows);
    }

    private static RepositoryPackEntryResponse entry(Pack pack, String rootPath) {
        return new RepositoryPackEntryResponse(pack.getId(), pack.getName(), null, pack.getCurrentVersionId(), false, rootPath, null, null);
    }

    private static RepositoryPackUpdateRequest request(UUID... packIds) {
        return new RepositoryPackUpdateRequest(Arrays.stream(packIds).map(id -> new RepositoryPackItemRequest(id, null)).toList());
    }

    private static List<PackMount> atRoot(UUID... packIds) {
        return Arrays.stream(packIds).map(PackMount::atRoot).toList();
    }

    private Composition composition(List<Version> versions) {
        return new Composition(repositoryId, List.of(), List.of(), versions, null, List.of(), List.of(), List.of(), Map.of());
    }

    @Test
    void replacePacks_unchangedOrder_releasesNothing() {
        storedOrder(high, low);

        service.replacePacks(actor, organizationId, repositoryId, request(high.getId(), low.getId()));

        verify(repositoryPackRepository, never()).deleteByRepositoryId(any());
        verify(releaseService, never()).releaseRepository(any(), any(), any());
        verify(releaseMapper).toPackResponse(eq(repositoryId), eq(List.of(entry(high, null), entry(low, null))), any());
    }

    @Test
    void replacePacks_newOrder_locksTouchedPacksInIdOrderThenSavesAndReleasesWithTheFirstComposedVersion() {
        storedOrder(high);
        UUID rootVersionId = UUID.randomUUID();
        Composition after = composition(List.of(Version.builder().id(rootVersionId).build()));
        when(composer.compose(organizationId, repositoryId, atRoot(high.getId(), low.getId()), Map.of())).thenReturn(after);
        when(validationQueryService.validateComposition(after.toCandidate())).thenReturn(List.of());
        Release release = Release.builder().id(UUID.randomUUID()).build();
        when(releaseService.releaseRepository(organizationId, repositoryId, rootVersionId)).thenReturn(Optional.of(release));

        service.replacePacks(actor, organizationId, repositoryId, request(high.getId(), low.getId()));

        InOrder order = inOrder(releaseService, repositoryPackRepository);
        order.verify(releaseService).lockOwner(low.getId());
        order.verify(releaseService).lockOwner(high.getId());
        order.verify(releaseService).lockReleases(repositoryId);
        order.verify(repositoryPackRepository).deleteByRepositoryId(repositoryId);
        ArgumentCaptor<List<RepositoryPack>> rows = ArgumentCaptor.captor();
        order.verify(repositoryPackRepository).saveAll(rows.capture());
        order.verify(releaseService).releaseRepository(organizationId, repositoryId, rootVersionId);
        assertEquals(List.of(high.getId(), low.getId()), rows.getValue().stream().map(RepositoryPack::getPackId).toList());
        assertEquals(List.of(0, 1), rows.getValue().stream().map(RepositoryPack::getPosition).toList());
        verify(releaseMapper).toPackResponse(repositoryId, List.of(entry(high, null), entry(low, null)), release);
    }

    @Test
    void replacePacks_onePack_namesItsComposedVersion() {
        storedOrder();
        Version composed = Version.builder().id(UUID.randomUUID()).build();
        when(composer.compose(organizationId, repositoryId, atRoot(low.getId()), Map.of())).thenReturn(composition(List.of(composed)));
        when(validationQueryService.validateComposition(any())).thenReturn(List.of());

        service.replacePacks(actor, organizationId, repositoryId, request(low.getId()));

        verify(releaseService).releaseRepository(organizationId, repositoryId, composed.getId());
    }

    @Test
    void replacePacks_compositionViolation_savesNothing() {
        storedOrder();
        when(composer.compose(eq(organizationId), eq(repositoryId), anyList(), eq(Map.of()))).thenReturn(composition(List.of()));
        when(validationQueryService.validateComposition(any()))
                .thenReturn(List.of(Violation.ofRepository(repositoryId, "AGENTS.md", ViolationRule.SYMLINK_DANGLING, "dangling")));

        assertThrows(ValidationException.class, () -> service.replacePacks(actor, organizationId, repositoryId,
                request(low.getId())));

        verify(repositoryPackRepository, never()).deleteByRepositoryId(any());
        verify(releaseService, never()).releaseRepository(any(), any(), any());
    }

    @Test
    void replacePacks_unknownPack_throwsNotFoundException() {
        storedOrder();
        UUID unknown = UUID.randomUUID();

        assertThrows(NotFoundException.class, () -> service.replacePacks(actor, organizationId, repositoryId,
                request(low.getId(), unknown)));

        verify(repositoryPackRepository, never()).saveAll(any());
    }

    @Test
    void replacePacks_packListedTwice_throwsValidationException() {
        storedOrder();

        assertThrows(ValidationException.class, () -> service.replacePacks(actor, organizationId, repositoryId,
                request(low.getId(), low.getId())));

        verify(validationQueryService).requireValid(argThat(violations -> violations.size() == 1
                && violations.get(0).rule() == ViolationRule.PACK_DUPLICATE));
        verify(composer, never()).compose(any(), any(), anyList(), any());
    }

    private Composition withFile(UUID forRepository, String path) {
        return new Composition(forRepository, List.of(new ComposedFile(path, FileMode.FILE, "sha", 10, null, true, low.getId(),
                low.getCurrentVersionId(), null, null, null)), List.of(), List.of(), null, List.of(), List.of(), List.of(), Map.of());
    }

    @Test
    void setPackRequired_unchangedFlag_writesAndReleasesNothing() {
        low.setRequired(true);
        when(packQueryService.getEntityById(organizationId, low.getId())).thenReturn(low);

        service.setPackRequired(actor, organizationId, low.getId(), new PackRequiredRequest(true));

        verify(packService, never()).setRequired(any(), any(), anyBoolean());
        verify(releaseService, never()).releaseRepository(any(), any(), any());
        verify(releaseMapper).toPackRequiredResponse(low.getId(), true, 0);
    }

    @Test
    void setPackRequired_changedFlag_locksThenReleasesOnlyTheRepositoriesWhoseCompositionChanged() {
        UUID other = UUID.randomUUID();
        when(packQueryService.getEntityById(organizationId, low.getId())).thenReturn(low);
        when(releaseQueryService.getActiveRepositoryIds(organizationId)).thenReturn(List.of(repositoryId, other));
        Composition before = composition(List.of());
        Composition after = withFile(repositoryId, ".agents/rules/guardrails/english.md");
        when(composer.compose(organizationId, repositoryId, Map.of())).thenReturn(before, after);
        when(composer.compose(organizationId, other, Map.of())).thenReturn(composition(List.of()), composition(List.of()));
        when(validationQueryService.validateComposition(after.toCandidate())).thenReturn(List.of());
        when(releaseService.releaseRepository(organizationId, repositoryId, low.getCurrentVersionId()))
                .thenReturn(Optional.of(Release.builder().id(UUID.randomUUID()).build()));

        service.setPackRequired(actor, organizationId, low.getId(), new PackRequiredRequest(true));

        InOrder order = inOrder(releaseService, packService);
        order.verify(releaseService).lockOwner(low.getId());
        order.verify(releaseService).lockReleases(repositoryId);
        order.verify(releaseService).lockReleases(other);
        order.verify(packService).setRequired(organizationId, low.getId(), true);
        order.verify(releaseService).releaseRepository(organizationId, repositoryId, low.getCurrentVersionId());
        verify(releaseService, never()).releaseRepository(organizationId, other, low.getCurrentVersionId());
        verify(releaseMapper).toPackRequiredResponse(low.getId(), true, 1);
    }

    @Test
    void setPackRequired_brokenComposition_throwsValidationExceptionAndReleasesNothing() {
        when(packQueryService.getEntityById(organizationId, low.getId())).thenReturn(low);
        when(releaseQueryService.getActiveRepositoryIds(organizationId)).thenReturn(List.of(repositoryId));
        when(composer.compose(organizationId, repositoryId, Map.of()))
                .thenReturn(composition(List.of()), withFile(repositoryId, ".agents/rules/guardrails/no-secrets.md"));
        when(validationQueryService.validateComposition(any())).thenReturn(List.of(Violation.ofRepository(repositoryId,
                ".agents/rules/guardrails/no-secrets.md", ViolationRule.GUARDRAIL_SETTINGS_MISSING, "no settings")));

        assertThrows(ValidationException.class, () -> service.setPackRequired(actor, organizationId, low.getId(),
                new PackRequiredRequest(true)));

        verify(releaseService, never()).releaseRepository(any(), any(), any());
    }

    @Test
    void setPackRequired_descendantOverridesAnItem_throwsItemLockedAndWritesNothing() {
        when(packQueryService.getEntityById(organizationId, low.getId())).thenReturn(low);
        List<Violation> overrides = List.of(new Violation(".agents/rules/core.md", ViolationRule.OVERRIDE_LOCKED, "locked", null,
                List.of(low.getId(), UUID.randomUUID())));
        when(releaseQueryService.getLockedOverrides(organizationId, low.getId())).thenReturn(overrides);
        ValidationException refusal = new ValidationException(ExceptionDomainKayaError.ITEM_LOCKED, Map.of());
        doThrow(refusal).when(validationQueryService).requireValid(overrides);

        ValidationException exception = assertThrows(ValidationException.class, () -> service.setPackRequired(actor,
                organizationId, low.getId(), new PackRequiredRequest(true)));

        assertEquals(refusal, exception);
        verify(packService, never()).setRequired(any(), any(), anyBoolean());
        verify(releaseService, never()).releaseRepository(any(), any(), any());
    }
}
