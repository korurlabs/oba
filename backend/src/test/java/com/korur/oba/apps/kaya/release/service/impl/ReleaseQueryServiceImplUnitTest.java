package com.korur.oba.apps.kaya.release.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.release.dto.response.EffectiveItemResponse;
import com.korur.oba.apps.kaya.release.dto.response.EffectiveOverrideResponse;
import com.korur.oba.apps.kaya.release.enums.ItemLock;
import com.korur.oba.apps.kaya.release.enums.OverrideStatus;
import com.korur.oba.apps.kaya.release.helper.Composer;
import com.korur.oba.apps.kaya.release.mapper.ReleaseMapper;
import com.korur.oba.apps.kaya.release.repository.ReleaseRepository;
import com.korur.oba.apps.kaya.release.repository.RepositoryPackRepository;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.entity.ItemOverride;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.service.ItemOverrideQueryService;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReleaseQueryServiceImplUnitTest {

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final String RULE = ".agents/rules/kagi/a.md";

    @Mock
    private ReleaseRepository releaseRepository;
    @Mock
    private RepositoryPackRepository repositoryPackRepository;
    @Mock
    private Composer composer;
    @Mock
    private RepositoryQueryService repositoryQueryService;
    @Mock
    private PackQueryService packQueryService;
    @Mock
    private ValidationQueryService validationQueryService;
    @Mock
    private VersionQueryService versionQueryService;
    @Mock
    private ItemOverrideQueryService itemOverrideQueryService;
    @Mock
    private ReleaseMapper releaseMapper;
    @Mock
    private LoggingService loggingService;

    private ReleaseQueryServiceImpl service;
    private final List<Pack> packs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ReleaseQueryServiceImpl(releaseRepository, repositoryPackRepository, composer, repositoryQueryService,
                packQueryService, validationQueryService, versionQueryService, itemOverrideQueryService, releaseMapper,
                loggingService);
        when(packQueryService.getChain(eq(ORGANIZATION), any()))
                .thenAnswer(invocation -> chainOf(invocation.getArgument(1)));
        when(packQueryService.getEntitiesByIds(eq(ORGANIZATION), anyCollection()))
                .thenAnswer(invocation -> packs.stream().filter(pack -> invocation.<Collection<UUID>>getArgument(1)
                        .contains(pack.getId())).toList());
        when(releaseMapper.toEffectiveItem(anyString(), any(), any(), any(), any(Boolean.class), any(), any()))
                .thenAnswer(invocation -> new EffectiveItemResponse(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4),
                        invocation.getArgument(5), invocation.getArgument(6)));
        when(releaseMapper.toEffectiveOverride(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> new EffectiveOverrideResponse(invocation.getArgument(0),
                        invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3),
                        invocation.getArgument(4), invocation.getArgument(5)));
    }

    private ItemOverride override(Pack child, String path, Pack base) {
        return ItemOverride.builder().organizationId(ORGANIZATION).versionId(child.getCurrentVersionId()).path(path)
                .basePackId(base.getId()).baseVersionId(base.getCurrentVersionId()).build();
    }

    private List<Pack> chainOf(UUID packId) {
        List<Pack> chain = new ArrayList<>();
        UUID next = packId;
        while (next != null) {
            UUID id = next;
            Pack found = packs.stream().filter(candidate -> candidate.getId().equals(id)).findFirst().orElseThrow();
            chain.add(0, found);
            next = found.getParentPackId();
        }
        return chain;
    }

    private Pack pack(String name, Pack parent, boolean required, List<String> paths) {
        UUID packId = UUID.randomUUID();
        Version version = Version.builder().id(UUID.randomUUID()).organizationId(ORGANIZATION).packId(packId).build();
        List<RenderedFile> files = paths.stream()
                .map(path -> new RenderedFile(path, FileMode.FILE, null, new byte[0], null, UUID.randomUUID(), null))
                .toList();
        when(versionQueryService.getEntityById(ORGANIZATION, version.getId())).thenReturn(version);
        when(versionQueryService.render(ORGANIZATION, version)).thenReturn(files);
        Pack pack = Pack.builder().id(packId).organizationId(ORGANIZATION).name(name)
                .parentPackId(parent == null ? null : parent.getId()).currentVersionId(version.getId()).build();
        pack.setRequired(required);
        packs.add(pack);
        when(packQueryService.getEntityById(ORGANIZATION, pack.getId())).thenReturn(pack);
        return pack;
    }

    @Test
    void getRuleScopeViolations_packMountedInTwoFolders_isCheckedAgainstBoth() {
        // Arrange
        Pack kagi = pack("kagi", null, false, List.of());
        when(repositoryPackRepository.findRootPathsByPackId(kagi.getId())).thenReturn(List.of("projects/kagi", "services/kagi"));
        byte[] content = "---\npaths: [\"projects/kagi/**\"]\n---\nKagi\n".getBytes(StandardCharsets.UTF_8);

        // Act
        List<Violation> violations = service.getRuleScopeViolations(ORGANIZATION, kagi.getId(), RULE, FileMode.FILE, content);

        // Assert
        assertEquals(1, violations.size());
        assertEquals(ViolationRule.MOUNT_RULE_SCOPE, violations.get(0).rule());
        assertEquals(RULE, violations.get(0).path());
        assertEquals("services/kagi", violations.get(0).rootPath());
    }

    @Test
    void getRuleScopeViolations_packMountedNowhere_isNoViolation() {
        // Arrange
        Pack kagi = pack("kagi", null, false, List.of());
        when(repositoryPackRepository.findRootPathsByPackId(kagi.getId())).thenReturn(List.of());

        // Act
        List<Violation> violations = service.getRuleScopeViolations(ORGANIZATION, kagi.getId(), RULE, FileMode.FILE,
                "# Always\n".getBytes(StandardCharsets.UTF_8));

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void getLockViolations_itemOfARequiredOrGuardrailAncestor_isLockedAndAnUnlockedItemIsNot() {
        // Arrange
        Pack org = pack("org", null, true, List.of(".agents/rules/core.md", ".agents/skills/kazan/SKILL.md"));
        Pack village = pack("village", org, false, List.of(".agents/rules/guardrails/no-push.md", ".agents/rules/village.md"));
        Pack kagi = pack("kagi", village, false, List.of());

        // Act
        List<Violation> violations = service.getLockViolations(ORGANIZATION, kagi.getId(), List.of(".agents/rules/core.md",
                ".agents/skills/kazan/extra.md", ".agents/rules/guardrails/no-push.md", ".agents/rules/village.md"));

        // Assert
        assertEquals(List.of(".agents/rules/core.md", ".agents/skills/kazan/", ".agents/rules/guardrails/no-push.md"),
                violations.stream().map(Violation::path).toList());
        assertTrue(violations.stream().allMatch(violation -> violation.rule() == ViolationRule.OVERRIDE_LOCKED));
        assertEquals(List.of(org.getId(), kagi.getId()), violations.get(0).ownerIds());
        assertEquals(List.of(village.getId(), kagi.getId()), violations.get(2).ownerIds());
    }

    @Test
    void getLockedOverrides_descendantsGivingThePacksItems_listsEachDescendantAndItem() {
        // Arrange
        Pack org = pack("org", null, false, List.of(".agents/rules/core.md", ".agents/rules/other.md"));
        Pack village = pack("village", org, false, List.of(".agents/rules/core.md", ".agents/rules/village.md"));
        Pack kagi = pack("kagi", village, false, List.of(".agents/rules/other.md"));
        when(packQueryService.getDescendantIds(ORGANIZATION, org.getId())).thenReturn(List.of(village.getId(), kagi.getId()));

        // Act
        List<Violation> violations = service.getLockedOverrides(ORGANIZATION, org.getId());

        // Assert
        assertEquals(2, violations.size());
        assertEquals(".agents/rules/core.md", violations.get(0).path());
        assertEquals(List.of(org.getId(), village.getId()), violations.get(0).ownerIds());
        assertEquals(".agents/rules/other.md", violations.get(1).path());
        assertEquals(List.of(org.getId(), kagi.getId()), violations.get(1).ownerIds());
    }

    @Test
    void getLockedOverrides_parentAddsAPathTheChildAlreadyHolds_isListedWithoutARecordedOverride() {
        // Arrange
        Pack org = pack("org", null, false, List.of(".agents/rules/core.md"));
        Pack village = pack("village", org, false, List.of(".agents/skills/kazan/SKILL.md", ".agents/rules/village.md"));
        when(packQueryService.getDescendantIds(ORGANIZATION, org.getId())).thenReturn(List.of(village.getId()));
        when(itemOverrideQueryService.getEntitiesByVersionIds(eq(ORGANIZATION), anyCollection())).thenReturn(List.of());
        // The parent's next version adds the skill; the child's version, saved before, records no override.
        Version added = Version.builder().id(UUID.randomUUID()).organizationId(ORGANIZATION).packId(org.getId()).build();
        when(versionQueryService.getEntityById(ORGANIZATION, added.getId())).thenReturn(added);
        when(versionQueryService.render(ORGANIZATION, added)).thenReturn(List.of(
                new RenderedFile(".agents/rules/core.md", FileMode.FILE, null, new byte[0], null, UUID.randomUUID(), null),
                new RenderedFile(".agents/skills/kazan/SKILL.md", FileMode.FILE, null, new byte[0], null, UUID.randomUUID(), null)));
        org.setCurrentVersionId(added.getId());

        // Act
        List<Violation> violations = service.getLockedOverrides(ORGANIZATION, org.getId());

        // Assert
        assertEquals(1, violations.size());
        assertEquals(ViolationRule.OVERRIDE_LOCKED, violations.get(0).rule());
        assertEquals(".agents/skills/kazan/", violations.get(0).path());
        assertEquals(List.of(org.getId(), village.getId()), violations.get(0).ownerIds());
    }

    @Test
    void getLockedOverrides_descendantGivingNoneOfThePacksItems_isNoViolation() {
        // Arrange
        Pack org = pack("org", null, false, List.of(".agents/rules/core.md"));
        Pack village = pack("village", org, false, List.of(".agents/rules/village.md"));
        when(packQueryService.getDescendantIds(ORGANIZATION, org.getId())).thenReturn(List.of(village.getId()));

        // Act
        List<Violation> violations = service.getLockedOverrides(ORGANIZATION, org.getId());

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void getEffective_ownItemsByDefault_listsOnlyOwnItemsWithTheirLocks() {
        // Arrange
        Pack org = pack("org", null, true, List.of(".agents/rules/core.md", ".agents/rules/org.md"));
        Pack kagi = pack("kagi", org, false, List.of(".agents/rules/kagi.md", ".agents/skills/deploy/SKILL.md"));

        // Act
        List<EffectiveItemResponse> items = service.getEffective(ORGANIZATION, kagi.getId(), null, false);

        // Assert
        assertEquals(List.of(".agents/rules/kagi.md", ".agents/skills/deploy/"),
                items.stream().map(EffectiveItemResponse::path).toList());
        assertTrue(items.stream().allMatch(EffectiveItemResponse::own));
        assertTrue(items.stream().allMatch(item -> item.override() == null && item.locked() == null));
    }

    @Test
    void getEffective_inheritedRequested_addsTheNearestAncestorsItemsWithTheirSource() {
        // Arrange
        Pack org = pack("org", null, true, List.of(".agents/rules/core.md", ".agents/rules/shared.md"));
        Pack village = pack("village", org, false, List.of(".agents/rules/shared.md",
                ".agents/rules/guardrails/no-push.md"));
        Pack kagi = pack("kagi", village, false, List.of(".agents/rules/kagi.md"));

        // Act
        List<EffectiveItemResponse> items = service.getEffective(ORGANIZATION, kagi.getId(), null, true);

        // Assert
        assertEquals(List.of(".agents/rules/core.md", ".agents/rules/guardrails/no-push.md", ".agents/rules/kagi.md",
                ".agents/rules/shared.md"), items.stream().map(EffectiveItemResponse::path).toList());
        assertEquals(org.getId(), items.get(0).sourcePackId());
        assertEquals(ItemLock.REQUIRED, items.get(0).locked());
        assertEquals(ItemLock.GUARDRAIL, items.get(1).locked());
        assertTrue(items.get(2).own());
        assertFalse(items.get(3).own());
        assertEquals(village.getId(), items.get(3).sourcePackId());
        assertEquals(village.getCurrentVersionId(), items.get(3).sourceVersionId());
    }

    @Test
    void getEffective_overrideOfAnUnchangedParentItem_isCurrent() {
        // Arrange
        Pack org = pack("org", null, false, List.of(".agents/rules/core.md"));
        Pack kagi = pack("kagi", org, false, List.of(".agents/rules/core.md"));
        when(itemOverrideQueryService.getEntitiesByVersionId(ORGANIZATION, kagi.getCurrentVersionId()))
                .thenReturn(List.of(override(kagi, ".agents/rules/core.md", org)));
        when(itemOverrideQueryService.isItemUnchanged(ORGANIZATION, org.getCurrentVersionId(),
                org.getCurrentVersionId(), ".agents/rules/core.md")).thenReturn(true);
        when(itemOverrideQueryService.getItemNumber(ORGANIZATION, org.getCurrentVersionId(), ".agents/rules/core.md"))
                .thenReturn(3);

        // Act
        EffectiveOverrideResponse override = service.getEffective(ORGANIZATION, kagi.getId(), null, false).get(0)
                .override();

        // Assert
        assertEquals(OverrideStatus.CURRENT, override.status());
        assertEquals(3, override.baseItemVersion());
        assertEquals(3, override.parentItemVersion());
        assertEquals(org.getId(), override.basePackId());
    }

    @Test
    void getEffective_overrideOfAChangedParentItem_isParentUpdatedWithBothItemNumbers() {
        // Arrange
        Pack org = pack("org", null, false, List.of(".agents/rules/core.md"));
        Pack kagi = pack("kagi", org, false, List.of(".agents/rules/core.md"));
        UUID baseVersionId = UUID.randomUUID();
        ItemOverride recorded = override(kagi, ".agents/rules/core.md", org);
        recorded.setBaseVersionId(baseVersionId);
        when(itemOverrideQueryService.getEntitiesByVersionId(ORGANIZATION, kagi.getCurrentVersionId()))
                .thenReturn(List.of(recorded));
        when(itemOverrideQueryService.isItemUnchanged(ORGANIZATION, baseVersionId, org.getCurrentVersionId(),
                ".agents/rules/core.md")).thenReturn(false);
        when(itemOverrideQueryService.getItemNumber(ORGANIZATION, baseVersionId, ".agents/rules/core.md")).thenReturn(4);
        when(itemOverrideQueryService.getItemNumber(ORGANIZATION, org.getCurrentVersionId(), ".agents/rules/core.md"))
                .thenReturn(5);

        // Act
        EffectiveOverrideResponse override = service.getEffective(ORGANIZATION, kagi.getId(), null, false).get(0)
                .override();

        // Assert
        assertEquals(OverrideStatus.PARENT_UPDATED, override.status());
        assertEquals(4, override.baseItemVersion());
        assertEquals(5, override.parentItemVersion());
    }

    @Test
    void getEffective_overrideWithoutARecordedBase_isParentUpdated() {
        // Arrange
        Pack org = pack("org", null, false, List.of(".agents/rules/core.md"));
        Pack kagi = pack("kagi", org, false, List.of(".agents/rules/core.md"));
        when(itemOverrideQueryService.getEntitiesByVersionId(ORGANIZATION, kagi.getCurrentVersionId()))
                .thenReturn(List.of());

        // Act
        EffectiveOverrideResponse override = service.getEffective(ORGANIZATION, kagi.getId(), null, false).get(0)
                .override();

        // Assert
        assertEquals(OverrideStatus.PARENT_UPDATED, override.status());
        assertNull(override.baseVersionId());
        assertNull(override.baseItemVersion());
    }

    @Test
    void getEffective_recordedOverrideNoAncestorDeliversAnyMore_isParentRemoved() {
        // Arrange
        Pack org = pack("org", null, false, List.of());
        Pack kagi = pack("kagi", org, false, List.of(".agents/rules/core.md"));
        when(itemOverrideQueryService.getEntitiesByVersionId(ORGANIZATION, kagi.getCurrentVersionId()))
                .thenReturn(List.of(override(kagi, ".agents/rules/core.md", org)));

        // Act
        EffectiveOverrideResponse override = service.getEffective(ORGANIZATION, kagi.getId(), null, false).get(0)
                .override();

        // Assert
        assertEquals(OverrideStatus.PARENT_REMOVED, override.status());
        assertNull(override.parentVersionId());
    }

    @Test
    void getEffective_threeLevels_comparesAgainstTheNearestAncestorPerLevel() {
        // Arrange
        Pack org = pack("org", null, false, List.of(".agents/rules/core.md"));
        Pack village = pack("village", org, false, List.of(".agents/rules/core.md"));
        Pack kagi = pack("kagi", village, false, List.of(".agents/rules/core.md"));
        when(itemOverrideQueryService.getEntitiesByVersionId(ORGANIZATION, village.getCurrentVersionId()))
                .thenReturn(List.of(override(village, ".agents/rules/core.md", org)));
        UUID olderVillageVersionId = UUID.randomUUID();
        ItemOverride kagiRecorded = override(kagi, ".agents/rules/core.md", village);
        kagiRecorded.setBaseVersionId(olderVillageVersionId);
        when(itemOverrideQueryService.getEntitiesByVersionId(ORGANIZATION, kagi.getCurrentVersionId()))
                .thenReturn(List.of(kagiRecorded));
        when(itemOverrideQueryService.isItemUnchanged(ORGANIZATION, org.getCurrentVersionId(),
                org.getCurrentVersionId(), ".agents/rules/core.md")).thenReturn(true);
        when(itemOverrideQueryService.isItemUnchanged(ORGANIZATION, olderVillageVersionId,
                village.getCurrentVersionId(), ".agents/rules/core.md")).thenReturn(false);

        // Act
        EffectiveOverrideResponse villageOverride = service.getEffective(ORGANIZATION, village.getId(), null, false)
                .get(0).override();
        EffectiveOverrideResponse kagiOverride = service.getEffective(ORGANIZATION, kagi.getId(), null, false)
                .get(0).override();

        // Assert
        assertEquals(OverrideStatus.CURRENT, villageOverride.status());
        assertEquals(org.getCurrentVersionId(), villageOverride.parentVersionId());
        assertEquals(OverrideStatus.PARENT_UPDATED, kagiOverride.status());
        assertEquals(village.getCurrentVersionId(), kagiOverride.parentVersionId());
    }

    @Test
    void getEffective_versionOfAnotherPack_isNotFound() {
        // Arrange
        Pack org = pack("org", null, false, List.of());
        Pack kagi = pack("kagi", org, false, List.of());

        // Act and assert
        assertThrows(NotFoundException.class,
                () -> service.getEffective(ORGANIZATION, kagi.getId(), org.getCurrentVersionId(), false));
    }
}
