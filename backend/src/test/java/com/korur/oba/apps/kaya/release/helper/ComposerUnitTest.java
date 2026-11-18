package com.korur.oba.apps.kaya.release.helper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.release.dto.embedded.PackVersionEntry;
import com.korur.oba.apps.kaya.release.entity.RepositoryPack;
import com.korur.oba.apps.kaya.release.repository.RepositoryPackRepository;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.settings.helper.SettingsParser;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;
import com.korur.oba.apps.kaya.validation.helper.CompositionRules;
import com.korur.oba.apps.kaya.validation.helper.MountCandidate;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComposerUnitTest {

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID REPOSITORY = UUID.randomUUID();
    private static final String WIDE_RULE = "---\npaths: [\"projects/kagi/**\", \"projects/kazan/**\"]\n---\nRoot rule\n";
    private static final String INSIDE_RULE = "---\npaths: [\"projects/kagi/backend/**\"]\n---\nInside rule\n";
    private static final String ALWAYS_ON_RULE = "# Core\n";
    private static final String SPANNING_RULE = "---\npaths: [\"projects/*/backend/**\"]\n---\nSpanning rule\n";
    private static final String KAGI_RULE = "---\npaths: [\"projects/kagi/**\"]\n---\nKagi rule\n";
    private static final String KAGI = "projects/kagi";

    @Mock
    private RepositoryPackRepository repositoryPackRepository;
    @Mock
    private RepositoryQueryService repositoryQueryService;
    @Mock
    private PackQueryService packQueryService;
    @Mock
    private VersionQueryService versionQueryService;

    private Composer composer;
    private Pack pack;
    private final List<Pack> packs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        composer = new Composer(repositoryPackRepository, repositoryQueryService, packQueryService,
                versionQueryService, new SettingsParser(new ObjectMapper()));
        when(repositoryQueryService.getEntityById(ORGANIZATION, REPOSITORY))
                .thenReturn(Repository.builder().id(REPOSITORY).organizationId(ORGANIZATION).name("village").build());
        when(packQueryService.getEntitiesByIds(eq(ORGANIZATION), anyCollection())).thenReturn(packs);
        when(packQueryService.getChain(eq(ORGANIZATION), any())).thenAnswer(invocation -> chainOf(invocation.getArgument(1)));
        pack = pack("base", List.of(
                file(".agents/rules/16-kagi.md", WIDE_RULE, null),
                file(".agents/rules/inside.md", INSIDE_RULE, null),
                file(".agents/rules/core.md", ALWAYS_ON_RULE, null),
                file(".agents/rules/spanning.md", SPANNING_RULE, null)));
    }

    /** The chain the pack service gives, root first, from the parents of the test packs. */
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

    private Version version(List<RenderedFile> files) {
        Version version = Version.builder().id(UUID.randomUUID()).organizationId(ORGANIZATION).build();
        when(versionQueryService.getEntityById(ORGANIZATION, version.getId())).thenReturn(version);
        when(versionQueryService.render(ORGANIZATION, version)).thenReturn(files);
        return version;
    }

    private Pack pack(String name, List<RenderedFile> files) {
        return child(name, null, files);
    }

    private Pack child(String name, Pack parent, List<RenderedFile> files) {
        Pack created = Pack.builder().id(UUID.randomUUID()).organizationId(ORGANIZATION).name(name)
                .parentPackId(parent == null ? null : parent.getId()).currentVersionId(version(files).getId()).build();
        packs.add(created);
        return created;
    }

    private static RenderedFile file(String path, String content, String overridesPath) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new RenderedFile(path, FileMode.FILE, null, bytes, sha256Of(bytes), UUID.randomUUID(), overridesPath);
    }

    private static RenderedFile link(String path, String target) {
        return new RenderedFile(path, FileMode.SYMLINK, target, null, null, null, null);
    }

    private static List<PackMount> atRoot(Pack... roots) {
        return Arrays.stream(roots).map(root -> PackMount.atRoot(root.getId())).toList();
    }

    private Composition compose(Map<UUID, UUID> replaced) {
        return composer.compose(ORGANIZATION, REPOSITORY, packs.stream().map(listed -> PackMount.atRoot(listed.getId())).toList(),
                replaced);
    }

    private Composition compose(List<PackMount> mounts) {
        return composer.compose(ORGANIZATION, REPOSITORY, mounts, Map.of());
    }

    private static List<ViolationRule> rules(List<Violation> violations) {
        return violations.stream().map(Violation::rule).toList();
    }

    private static String sha256Of(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void compose_storedPacks_composesEveryPackAtItsCurrentVersionWithItsFolder() {
        Pack second = pack("second", List.of(file(".agents/roles/a.md", "# A\n", null)));
        Pack kagi = pack("kagi", List.of(file(".agents/rules/kagi/a.md", KAGI_RULE, null)));
        when(repositoryPackRepository.findByRepositoryIdOrderByPositionAsc(REPOSITORY)).thenReturn(List.of(
                RepositoryPack.builder().repositoryId(REPOSITORY).packId(second.getId()).position(0).build(),
                RepositoryPack.builder().repositoryId(REPOSITORY).packId(pack.getId()).position(1).build(),
                RepositoryPack.builder().repositoryId(REPOSITORY).packId(kagi.getId()).position(2).rootPath(KAGI).build()));

        Composition composition = composer.compose(ORGANIZATION, REPOSITORY, Map.of());

        assertEquals(List.of(pack.getCurrentVersionId(), second.getCurrentVersionId(), kagi.getCurrentVersionId()),
                composition.versions().stream().map(Version::getId).toList());
        assertEquals(List.of(new PackVersionEntry(pack.getId(), pack.getCurrentVersionId(), null),
                new PackVersionEntry(second.getId(), second.getCurrentVersionId(), null),
                new PackVersionEntry(kagi.getId(), kagi.getCurrentVersionId(), KAGI)), composition.packVersions());
        assertEquals(second.getId(), composition.byPath().get(".agents/roles/a.md").ownerId());
        assertTrue(composition.byPath().get(".agents/rules/core.md").alwaysOn());
        assertFalse(composition.byPath().get(".agents/rules/inside.md").alwaysOn());
    }

    @Test
    void compose_replacedVersion_composesItInsteadOfTheCurrentOne() {
        Version preview = version(List.of(file(".agents/roles/new.md", "# New\n", null)));

        Composition composition = compose(Map.of(pack.getId(), preview.getId()));

        assertEquals(List.of(".agents/roles/new.md"), composition.files().stream().map(ComposedFile::path).toList());
        assertEquals(preview.getId(), composition.byPath().get(".agents/roles/new.md").versionId());
        assertEquals(preview.getId(), composition.packVersions().get(0).versionId());
        verify(versionQueryService, never()).getEntityById(ORGANIZATION, pack.getCurrentVersionId());
    }

    @Test
    void compose_packWithoutCurrentVersion_isLeftOutOfTheVersionsAndListedWithoutOne() {
        Pack empty = Pack.builder().id(UUID.randomUUID()).organizationId(ORGANIZATION).name("empty").build();
        packs.add(empty);

        Composition composition = compose(Map.of());

        assertEquals(1, composition.versions().size());
        assertTrue(composition.packVersions().contains(new PackVersionEntry(empty.getId(), null, null)));
    }

    @Test
    void compose_samePathInTwoRootPacks_keepsTheLastAndReportsAConflict() {
        Pack second = pack("second", List.of(file(".agents/rules/core.md", "# Other\n", null)));

        Composition composition = compose(Map.of());

        assertEquals(second.getId(), composition.byPath().get(".agents/rules/core.md").ownerId());
        assertEquals(1, composition.conflicts().size());
        Violation conflict = composition.conflicts().get(0);
        assertEquals(ViolationRule.PATH_IN_TWO_SETS, conflict.rule());
        assertEquals(".agents/rules/core.md", conflict.path());
        assertEquals(List.of(pack.getId(), second.getId()).stream().sorted().toList(), conflict.ownerIds());
    }

    @Test
    void compose_childOfARootPack_composesTheChainRootFirstAndTheDeepestGivesTheSharedPath() {
        Pack child = child("aaa-child", pack, List.of(file(".agents/rules/core.md", "# Child\n", null)));
        Pack grandchild = child("aaa-grandchild", child, List.of(file(".agents/rules/core.md", "# Grandchild\n", null),
                file(".agents/rules/inside.md", "# Own\n", null)));

        Composition composition = compose(atRoot(grandchild));

        assertEquals(List.of(pack.getId(), child.getId(), grandchild.getId()),
                composition.packVersions().stream().map(PackVersionEntry::packId).toList());
        assertTrue(composition.conflicts().isEmpty());
        assertEquals(grandchild.getId(), composition.byPath().get(".agents/rules/core.md").ownerId());
        assertEquals(grandchild.getId(), composition.byPath().get(".agents/rules/inside.md").ownerId());
        assertEquals(pack.getId(), composition.byPath().get(".agents/rules/16-kagi.md").ownerId());
    }

    @Test
    void compose_siblingsUnderOneParent_conflictOnASharedPathTheParentAlsoGives() {
        Pack left = child("left", pack, List.of(file(".agents/rules/core.md", "# Left\n", null)));
        Pack right = child("right", pack, List.of(file(".agents/rules/core.md", "# Right\n", null)));

        Composition composition = compose(atRoot(left, right));

        assertEquals(1, composition.conflicts().size());
        assertEquals(List.of(pack.getId(), left.getId(), right.getId()).stream().sorted().toList(),
                composition.conflicts().get(0).ownerIds());
    }

    @Test
    void compose_childGivingAnItemOfARequiredAncestor_isOverrideLocked() {
        pack.setRequired(true);
        Pack child = child("aaa-child", pack, List.of(file(".agents/rules/core.md", "# Child\n", null)));

        Composition composition = compose(atRoot(child));

        List<Violation> locked = composition.mountViolations().stream()
                .filter(violation -> violation.rule() == ViolationRule.OVERRIDE_LOCKED)
                .toList();
        assertEquals(1, locked.size());
        assertEquals(".agents/rules/core.md", locked.get(0).path());
        assertEquals(List.of(pack.getId(), child.getId()), locked.get(0).ownerIds());
    }

    @Test
    void compose_childAddingAFileToASkillOfARequiredAncestor_isOverrideLockedOnTheSkill() {
        Pack parent = pack("org", List.of(file(".agents/skills/kazan/SKILL.md", "---\nname: kazan\n---\nBody\n", null)));
        parent.setRequired(true);
        Pack child = child("village", parent, List.of(file(".agents/skills/kazan/extra.md", "# Extra\n", null)));

        Composition composition = compose(atRoot(child));

        assertEquals(List.of(".agents/skills/kazan/"), composition.mountViolations().stream()
                .filter(violation -> violation.rule() == ViolationRule.OVERRIDE_LOCKED)
                .map(Violation::path)
                .toList());
    }

    @Test
    void compose_childGivingAGuardrailOfAnAncestor_isOverrideLockedWithoutARequiredAncestor() {
        Pack parent = pack("org", List.of(file(".agents/rules/guardrails/no-push.md", "# Parent\n", null)));
        Pack child = child("village", parent, List.of(file(".agents/rules/guardrails/no-push.md", "# Child\n", null)));

        Composition composition = compose(atRoot(child));

        assertEquals(List.of(".agents/rules/guardrails/no-push.md"), composition.mountViolations().stream()
                .filter(violation -> violation.rule() == ViolationRule.OVERRIDE_LOCKED)
                .map(Violation::path)
                .toList());
    }

    @Test
    void compose_childOverridingAnUnlockedItem_isNoLockViolation() {
        Pack child = child("aaa-child", pack, List.of(file(".agents/rules/core.md", "# Child\n", null)));

        Composition composition = compose(atRoot(child));

        assertFalse(rules(composition.mountViolations()).contains(ViolationRule.OVERRIDE_LOCKED));
    }

    @Test
    void compose_settingsWithHooks_readsTheHookRegistrations() {
        pack("settings", List.of(file(".claude/settings.json",
                "{\"hooks\":{\"PreToolUse\":[{\"matcher\":\"Bash\",\"hooks\":[{\"type\":\"command\",\"command\":\"x.sh\"}]}]}}", null)));

        Composition composition = compose(Map.of());

        assertNotNull(composition.settingsHooks());
        assertEquals(1, composition.settingsHooks().size());
    }

    @Test
    void compose_requiredPack_isComposedOnceAtTheRootWhetherListedOrNot() {
        Pack own = pack("own", List.of(file(".agents/rules/own.md", "# Own\n", null)));
        Pack required = pack("required", List.of(file(".agents/rules/required.md", "# Required\n", null)));
        when(packQueryService.getRequiredPackIds(ORGANIZATION)).thenReturn(List.of(required.getId()));

        Composition listed = compose(atRoot(own, required));
        Composition unlisted = compose(atRoot(own));

        for (Composition composition : List.of(listed, unlisted)) {
            assertEquals(required.getId(), composition.byPath().get(".agents/rules/required.md").ownerId());
            assertEquals(List.of(own.getCurrentVersionId(), required.getCurrentVersionId()),
                    composition.versions().stream().map(Version::getId).toList());
        }
    }

    @Test
    void compose_mountedPack_deliversItsAgentsFileInTheFolderAndComposesItsAncestorsAtTheRoot() {
        Pack kagi = child("kagi", pack, List.of(file("AGENTS.md", "# Kagi\n", null),
                file(".agents/rules/kagi/backend.md", KAGI_RULE, null),
                file(".agents/skills/kagi-api/SKILL.md", "---\nname: kagi-api\ndescription: x\n---\n", null)));

        Composition composition = compose(List.of(new PackMount(kagi.getId(), KAGI)));

        assertEquals(List.of(new PackVersionEntry(pack.getId(), pack.getCurrentVersionId(), null),
                new PackVersionEntry(kagi.getId(), kagi.getCurrentVersionId(), KAGI)), composition.packVersions());
        assertNull(composition.byPath().get("AGENTS.md"));
        assertEquals(kagi.getId(), composition.byPath().get("projects/kagi/AGENTS.md").ownerId());
        assertEquals(kagi.getId(), composition.byPath().get(".agents/rules/kagi/backend.md").ownerId());
        assertEquals(kagi.getId(), composition.byPath().get(".agents/skills/kagi-api/SKILL.md").ownerId());
        assertEquals(pack.getId(), composition.byPath().get(".agents/rules/core.md").ownerId());
        assertTrue(composition.mountViolations().isEmpty());
        assertTrue(new CompositionRules().validate(composition.toCandidate()).isEmpty());
    }

    @Test
    void compose_mountedPackWithRootOnlyKinds_isRefusedEachOfThemByTheCompositionRules() {
        Pack kagi = pack("kagi", List.of(file(".agents/AGENTS.md", "# Dir\n", null),
                file(".claude/settings.json", "{}", null),
                file(".agents/hooks/check.sh", "#!/bin/sh\n", null),
                link("CLAUDE.md", "AGENTS.md"),
                file(".agents/rules/guardrails/no-secrets.md", "---\ncategory: SECURITY\n---\nNo.\n", null)));

        Composition composition = compose(List.of(new PackMount(kagi.getId(), KAGI)));

        List<Violation> violations = new CompositionRules().validate(composition.toCandidate());
        for (String path : List.of(".agents/AGENTS.md", ".claude/settings.json", ".agents/hooks/check.sh", "CLAUDE.md")) {
            assertTrue(violations.stream().anyMatch(violation -> path.equals(violation.path())
                    && violation.rule() == ViolationRule.MOUNT_PATH_OUTSIDE_NAMESPACE), path);
        }
        assertTrue(violations.stream().anyMatch(violation -> ".agents/rules/guardrails/no-secrets.md".equals(violation.path())
                && violation.rule() == ViolationRule.GUARDRAIL_SCOPE));
    }

    @Test
    void compose_mountedPackGivingARootPath_isAConflict() {
        Pack kagi = pack("kagi", List.of(file(".agents/rules/core.md", "# Kagi core\n", null)));

        Composition composition = compose(List.of(PackMount.atRoot(pack.getId()), new PackMount(kagi.getId(), KAGI)));

        assertEquals(pack.getId(), composition.byPath().get(".agents/rules/core.md").ownerId());
        assertEquals(List.of(ViolationRule.PATH_IN_TWO_SETS), rules(composition.conflicts()));
    }

    @Test
    void compose_mountedPackThatIsAnAncestorOfARootPack_isPackMountInheritedAndComposedAtTheRootOnly() {
        Pack kagi = child("kagi", pack, List.of(file(".agents/rules/kagi/backend.md", KAGI_RULE, null)));
        Pack docs = child("kagi-docs", kagi, List.of(file(".agents/roles/docs.md", "# Docs\n", null)));

        Composition composition = compose(List.of(PackMount.atRoot(docs.getId()), new PackMount(kagi.getId(), KAGI)));

        assertEquals(List.of(ViolationRule.PACK_MOUNT_INHERITED), rules(composition.mountViolations()));
        assertEquals(List.of(kagi.getId()), composition.mountViolations().get(0).ownerIds());
        assertTrue(composition.mounts().isEmpty());
        assertEquals(List.of(pack.getId(), kagi.getId(), docs.getId()),
                composition.packVersions().stream().map(PackVersionEntry::packId).toList());
    }

    @Test
    void compose_mountedUnscopedRule_isDeliveredWithItsStoredBytesAndNamesItsFolder() {
        RenderedFile wide = file(".agents/rules/kagi/wide.md", "---\npaths: [\"{projects/kagi,projects/kazan}/**\"]\n---\nWide\n",
                null);
        Pack kagi = pack("kagi", List.of(wide));

        Composition composition = compose(List.of(new PackMount(kagi.getId(), KAGI)));

        Violation violation = composition.mountViolations().stream()
                .filter(candidate -> candidate.rule() == ViolationRule.MOUNT_RULE_SCOPE)
                .findFirst()
                .orElseThrow();
        assertEquals(KAGI, violation.rootPath());
        assertEquals(List.of(kagi.getId()), violation.ownerIds());
        assertEquals(wide.sha256(), composition.byPath().get(wide.path()).sha256());
        assertNull(composition.byPath().get(wide.path()).narrowedBy());
    }

    @Test
    void ruleScopeViolation_patternsOfSection8_refusesEveryPatternNotUnderTheFolder() {
        MountCandidate mount = new MountCandidate(UUID.randomUUID(), "kagi", KAGI);
        String rule = ".agents/rules/kagi/a.md";

        for (String refused : List.of("# No paths\n", "---\npaths: [\"**/*.java\"]\n---\nA\n",
                "---\npaths: [\"projects/kagi*/**\"]\n---\nA\n", "---\npaths: [\"{projects/kagi,projects/kazan}/**\"]\n---\nA\n")) {
            assertTrue(Composer.ruleScopeViolation(null, mount, rule, FileMode.FILE, refused.getBytes(StandardCharsets.UTF_8))
                    .isPresent(), refused);
        }
        for (String accepted : List.of("---\npaths: [\"projects/kagi/**\"]\n---\nA\n",
                "---\npaths: [\"./projects/kagi/src/**\"]\n---\nA\n")) {
            assertTrue(Composer.ruleScopeViolation(null, mount, rule, FileMode.FILE, accepted.getBytes(StandardCharsets.UTF_8))
                    .isEmpty(), accepted);
        }
    }

    @Test
    void ruleScopeViolation_guardrailOfAPackNamedGuardrails_isNoRuleScopeViolation() {
        MountCandidate mount = new MountCandidate(UUID.randomUUID(), "guardrails", KAGI);
        byte[] guardrail = "---\ncategory: SECURITY\ndeny: [\"Read(./.env)\"]\n---\nNo secrets.\n".getBytes(StandardCharsets.UTF_8);

        assertTrue(Composer.ruleScopeViolation(null, mount, ".agents/rules/guardrails/no-secrets.md", FileMode.FILE, guardrail)
                .isEmpty());
    }

    @Test
    void compose_mountedPackNamedGuardrailsGivingAGuardrail_isGuardrailScopeAndNotMountRuleScope() {
        Pack guardrails = pack("guardrails", List.of(file(".agents/rules/guardrails/no-secrets.md",
                "---\ncategory: SECURITY\ndeny: [\"Read(./.env)\"]\n---\nNo secrets.\n", null)));

        Composition composition = compose(List.of(new PackMount(guardrails.getId(), KAGI)));

        assertTrue(composition.mountViolations().stream().noneMatch(violation -> violation.rule() == ViolationRule.MOUNT_RULE_SCOPE));
        assertTrue(new CompositionRules().validate(composition.toCandidate()).stream()
                .anyMatch(violation -> ".agents/rules/guardrails/no-secrets.md".equals(violation.path())
                        && violation.rule() == ViolationRule.GUARDRAIL_SCOPE));
    }

    @Test
    void compose_mountedRuleWithoutPathsOrOutsideTheFolder_isMountRuleScope() {
        Pack kagi = pack("kagi", List.of(file(".agents/rules/kagi/inside.md", KAGI_RULE, null),
                file(".agents/rules/kagi/always.md", "# Always\n", null),
                file(".agents/rules/kagi/wide.md", "---\npaths: [\"**/*.java\"]\n---\nWide\n", null)));

        Composition composition = compose(List.of(new PackMount(kagi.getId(), KAGI)));

        assertEquals(List.of(".agents/rules/kagi/always.md", ".agents/rules/kagi/wide.md"), composition.mountViolations().stream()
                .filter(violation -> violation.rule() == ViolationRule.MOUNT_RULE_SCOPE)
                .map(Violation::path)
                .sorted()
                .toList());
    }

    @Test
    void compose_mountedRuleOverridingARootRule_narrowsTheRootRuleToTheOtherFolders() {
        Pack kagi = pack("kagi", List.of(file(".agents/rules/kagi/16-kagi.md", KAGI_RULE, ".agents/rules/16-kagi.md")));

        Composition composition = compose(List.of(PackMount.atRoot(pack.getId()), new PackMount(kagi.getId(), KAGI)));

        ComposedFile narrowed = composition.byPath().get(".agents/rules/16-kagi.md");
        assertEquals(kagi.getId(), narrowed.narrowedBy());
        String content = new String(composition.contents().get(narrowed.sha256()), StandardCharsets.UTF_8);
        assertTrue(content.contains("projects/kazan/**"));
        assertFalse(content.contains("projects/kagi/**"));
        assertTrue(composition.mountViolations().isEmpty());
    }

    @Test
    void compose_guardrail_carriesTheDenyRulesOfItsFrontmatterAndIsAlwaysOn() {
        Pack guarded = pack("guarded", List.of(
                file(".agents/rules/guardrails/no-secrets.md",
                        "---\ncategory: SECURITY\ndeny: [\"Read(./.env)\", \"Bash(cat .env:*)\"]\n---\nNever read secrets.\n", null),
                file(".agents/rules/guardrails/english.md", "---\ncategory: LANGUAGE\n---\nAnswer in English.\n", null)));

        Composition composition = compose(atRoot(pack, guarded));

        ComposedFile blocking = composition.byPath().get(".agents/rules/guardrails/no-secrets.md");
        ComposedFile instructing = composition.byPath().get(".agents/rules/guardrails/english.md");
        assertEquals(List.of("Read(./.env)", "Bash(cat .env:*)"), blocking.deny());
        assertTrue(blocking.alwaysOn());
        assertNull(instructing.deny());
        assertTrue(instructing.alwaysOn());
        assertNull(composition.byPath().get(".agents/rules/core.md").deny());
    }
}
