package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.helper.FileCandidates;
import com.korur.oba.apps.kaya.settings.helper.SettingsParser;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionRulesUnitTest {

    private static final UUID REPOSITORY = UUID.randomUUID();
    private static final UUID SET = UUID.randomUUID();

    private final CompositionRules rules = new CompositionRules();

    private static PathCandidate file(String path, int size, boolean alwaysOn) {
        return new PathCandidate(path, false, null, size, alwaysOn, SET, null);
    }

    private static PathCandidate link(String path, String target) {
        return new PathCandidate(path, true, target, null, false, SET, null);
    }

    private List<ViolationRule> rulesOf(List<PathCandidate> files, String settings) {
        return rules.validate(composition(files, settings)).stream().map(Violation::rule).toList();
    }

    private static CompositionCandidate composition(List<PathCandidate> files, String settings) {
        return new CompositionCandidate(REPOSITORY, files, List.of(),
                settings == null ? null : hooksOf(settings), List.of(), List.of());
    }

    // The composer hands the rules the hooks the settings register, read with the one settings parser.
    private static List<HookRegistration> hooksOf(String settings) {
        return new SettingsParser(new ObjectMapper()).parse(settings.getBytes(StandardCharsets.UTF_8))
                .map(document -> document.hooks().stream()
                        .map(hook -> new HookRegistration(hook.event(), hook.command()))
                        .toList())
                .orElse(null);
    }

    @Test
    void validate_guardrailWithDenyRulesWithoutSettings_isGuardrailSettingsMissing() {
        PathCandidate blocking = new PathCandidate(".agents/rules/guardrails/no-secrets.md", false, null, 100, true, SET,
                List.of("Read(./.env)"));
        PathCandidate instructing = file(".agents/rules/guardrails/english.md", 100, true);

        assertEquals(List.of(ViolationRule.GUARDRAIL_SETTINGS_MISSING), rulesOf(List.of(blocking, instructing), null));
        assertEquals(List.of(), rulesOf(List.of(blocking, file(".claude/settings.json", 10, false)), "{}"));
        assertEquals(List.of(), rulesOf(List.of(instructing), null));
    }

    @Test
    void validate_symlinkToADeliveredDirectory_isFine() {
        List<PathCandidate> files = List.of(file(".agents/skills/kazan/SKILL.md", 10, false),
                link(".claude/skills/kazan", "../../.agents/skills/kazan"));
        assertEquals(List.of(), rulesOf(files, null));
    }

    @Test
    void validate_toolLinksToDeliveredDirectories_areFine() {
        List<PathCandidate> files = List.of(file(".agents/rules/core.md", 10, true), file(".agents/templates/HANDOFF.md", 10, false),
                link(".claude/rules", "../.agents/rules"), link(".claude/templates", "../.agents/templates"));
        assertEquals(List.of(), rulesOf(files, null));
    }

    @Test
    void validate_toolLinkToADirectoryWithoutFiles_isDangling() {
        List<PathCandidate> files = List.of(file(".agents/rules/core.md", 10, true), link(".claude/templates", "../.agents/templates"));
        assertEquals(List.of(ViolationRule.SYMLINK_DANGLING), rulesOf(files, null));
    }

    @Test
    void validate_linkToADirectoryKayaDoesNotDeliver_isRefused() {
        List<PathCandidate> files = List.of(file(".agents/rules/core.md", 10, true), link(".claude/rules", "../.git"),
                link(".claude/templates", "../.agents"));
        assertEquals(List.of(ViolationRule.SYMLINK_TARGET_NOT_ALLOWED, ViolationRule.SYMLINK_TARGET_NOT_ALLOWED),
                rulesOf(files, null));
    }

    @Test
    void validateLinks_toolLinkOfAVersion_isFine() {
        assertEquals(List.of(), linkRulesOf(List.of(candidateLink(".claude/rules", "../.agents/rules"))));
    }

    @Test
    void validate_symlinkToAnUndeliveredDeliveredPath_isDangling() {
        List<PathCandidate> files = List.of(link(".claude/skills/kazan", "../../.agents/skills/kazan"));
        assertEquals(List.of(ViolationRule.SYMLINK_DANGLING), rulesOf(files, null));
    }

    @Test
    void validate_symlinkLoop_isDangling() {
        List<PathCandidate> files = List.of(link(".agents/rules/a.md", "b.md"), link(".agents/rules/b.md", "a.md"));
        assertEquals(List.of(ViolationRule.SYMLINK_DANGLING, ViolationRule.SYMLINK_DANGLING),
                rulesOf(files, null));
    }

    @Test
    void validate_alwaysOnFilesOverFortyThousandBytes_isFine() {
        List<PathCandidate> files = List.of(file("AGENTS.md", 30000, true), file(".agents/rules/core.md", 20000, true));
        assertEquals(List.of(), rulesOf(files, null));
    }

    @Test
    void validate_agentsLinkToTheDeliveredAgentsFile_isFine() {
        PathCandidate agentsLink = new PathCandidate("AGENTS.md", true, ".agents/AGENTS.md", null, true, SET, null);
        List<PathCandidate> files = List.of(agentsLink, file(".agents/AGENTS.md", 1856, true));
        assertEquals(List.of(), rulesOf(files, null));
    }

    @Test
    void validate_ruleLinkToAFileThatIsNotAlwaysOn_isFine() {
        PathCandidate ruleLink = new PathCandidate(".agents/rules/big.md", true, "../skills/big/notes.md", null, true, SET, null);
        List<PathCandidate> files = List.of(ruleLink, file(".agents/skills/big/notes.md", 4000, false),
                file(".agents/skills/big/SKILL.md", 10, false));
        assertEquals(List.of(), rulesOf(files, null));
    }

    @Test
    void validate_alwaysOnLinkToADirectory_isSymlinkContentRequired() {
        PathCandidate ruleLink = new PathCandidate(".agents/rules/big", true, "../skills/big", null, true, SET, null);
        List<PathCandidate> files = List.of(ruleLink, file(".agents/skills/big/SKILL.md", 10, false));
        assertEquals(List.of(ViolationRule.SYMLINK_CONTENT_REQUIRED), rulesOf(files, null));
    }

    @Test
    void validate_dispatcherDeliveredButNotRegistered_isReportedPerEvent() {
        List<PathCandidate> files = List.of(file(".agents/hooks/dispatch.sh", 10, false), file(".claude/settings.json", 10, false));
        String onlyPreToolUse = """
                {"hooks":{"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":".agents/hooks/dispatch.sh"}]}]}}
                """;
        assertEquals(List.of(ViolationRule.SETTINGS_DISPATCHER_MISSING), rulesOf(files, onlyPreToolUse));
        String both = """
                {"hooks":{"PreToolUse":[{"hooks":[{"type":"command","command":"bash .agents/hooks/dispatch.sh pre"}]}],
                 "SessionStart":[{"hooks":[{"type":"command","command":".agents/hooks/dispatch.sh"}]}]}}
                """;
        assertEquals(List.of(), rulesOf(files, both));
    }

    @Test
    void validate_fileBelowALinkOutOfTheAllowList_isRefused() {
        List<PathCandidate> hooks = List.of(link(".agents/skills/x", "../../.git/hooks"),
                file(".agents/skills/x/pre-commit", 10, false));
        assertEquals(List.of(ViolationRule.SYMLINK_TARGET_NOT_ALLOWED, ViolationRule.PATH_THROUGH_SYMLINK),
                rulesOf(hooks, null));
        List<PathCandidate> workflows = List.of(link(".agents/skills/x", "../../.github/workflows"),
                file(".agents/skills/x/deploy.yml", 10, false));
        assertEquals(List.of(ViolationRule.SYMLINK_TARGET_NOT_ALLOWED, ViolationRule.PATH_THROUGH_SYMLINK),
                rulesOf(workflows, null));
    }

    @Test
    void validate_fileBelowALinkInsideTheAllowList_isStillRefused() {
        UUID otherSet = UUID.randomUUID();
        PathCandidate fromOtherSet = new PathCandidate(".claude/skills/kazan/run.js", false, null, 10, false, otherSet, null);
        List<PathCandidate> files = List.of(file(".agents/skills/kazan/SKILL.md", 10, false),
                link(".claude/skills/kazan", "../../.agents/skills/kazan"), fromOtherSet);
        assertEquals(List.of(ViolationRule.PATH_THROUGH_SYMLINK), rulesOf(files, null));
    }

    @Test
    void validate_chainThatLeavesTheRepository_isRefused() {
        List<PathCandidate> files = List.of(file(".agents/skills/s/SKILL.md", 10, false),
                link(".agents/skills/s/d/a", "../../.."), link(".agents/skills/s/d/b", "a/../../../.."));
        assertEquals(List.of(ViolationRule.SYMLINK_TARGET_NOT_ALLOWED, ViolationRule.SYMLINK_OUTSIDE_REPOSITORY),
                rulesOf(files, null));
    }

    @Test
    void validate_linkToALinkToADeliveredDirectory_isFine() {
        List<PathCandidate> files = List.of(file(".agents/skills/kazan/SKILL.md", 10, false),
                link(".agents/skills/alias", "kazan"), link(".claude/skills/kazan", "../../.agents/skills/alias"));
        assertEquals(List.of(), rulesOf(files, null));
    }

    private static FileCandidate candidate(String path) {
        return FileCandidates.of(path, FileMode.FILE, null, "x".getBytes(StandardCharsets.UTF_8));
    }

    private static FileCandidate candidateLink(String path, String target) {
        return FileCandidates.of(path, FileMode.SYMLINK, target, null);
    }

    private List<ViolationRule> linkRulesOf(List<FileCandidate> files) {
        return rules.validateLinks(files).stream().map(Violation::rule).toList();
    }

    @Test
    void validateLinks_fileBelowALinkOfTheVersion_isRefused() {
        List<Violation> violations = rules.validateLinks(List.of(candidateLink(".agents/skills/x", "../../.git/hooks"),
                candidate(".agents/skills/x/pre-commit")));
        assertEquals(List.of(ViolationRule.SYMLINK_TARGET_NOT_ALLOWED, ViolationRule.PATH_THROUGH_SYMLINK),
                violations.stream().map(Violation::rule).toList());
        assertEquals(List.of(".agents/skills/x", ".agents/skills/x/pre-commit"),
                violations.stream().map(Violation::path).toList());
        assertEquals(null, violations.getFirst().repositoryId());
    }

    @Test
    void validateLinks_chainThatLeavesTheRepository_isRefused() {
        List<FileCandidate> files = List.of(candidateLink(".agents/skills/s/d/a", "../../.."),
                candidateLink(".agents/skills/s/d/b", "a/../../../.."));
        assertEquals(List.of(ViolationRule.SYMLINK_TARGET_NOT_ALLOWED, ViolationRule.SYMLINK_OUTSIDE_REPOSITORY),
                linkRulesOf(files));
    }

    @Test
    void validateLinks_loop_isDangling() {
        List<FileCandidate> files = List.of(candidateLink(".agents/skills/x/a", "b"), candidateLink(".agents/skills/x/b", "a"));
        assertEquals(List.of(ViolationRule.SYMLINK_DANGLING, ViolationRule.SYMLINK_DANGLING), linkRulesOf(files));
    }

    @Test
    void validateLinks_targetFromAnotherSet_isLeftToTheComposition() {
        assertEquals(List.of(), linkRulesOf(List.of(candidateLink(".claude/skills/kazan", "../../.agents/skills/kazan"))));
        assertEquals(List.of(), linkRulesOf(List.of(candidate(".agents/skills/kazan/SKILL.md"),
                candidateLink(".agents/skills/alias", "kazan"), candidateLink(".claude/skills/kazan", "../../.agents/skills/alias"))));
    }

    @Test
    void validateLinks_linkTheFileRulesRefuse_isNotReportedTwice() {
        assertEquals(List.of(), linkRulesOf(List.of(candidateLink(".agents/skills/x/up", "../../../../.."),
                candidateLink(".agents/skills/x/abs", "/etc"), candidateLink(".agents/skills/x/blank", " "))));
    }

    @Test
    void validate_pathsFromTwoSetsThatDifferOnlyInCase_isPathCaseConflict() {
        PathCandidate fromOtherSet = new PathCandidate(".agents/skills/x/pre-commit", false, null, 10, false, UUID.randomUUID(), null);
        List<PathCandidate> files = List.of(file(".agents/skills/s/SKILL.md", 10, false),
                link(".agents/skills/X", "s"), fromOtherSet);
        CompositionCandidate composition = composition(files, null);
        List<Violation> violations = rules.validate(composition);
        assertEquals(List.of(ViolationRule.PATH_CASE_CONFLICT, ViolationRule.PATH_THROUGH_SYMLINK),
                violations.stream().map(Violation::rule).toList());
        assertEquals(".agents/skills/x/pre-commit", violations.getFirst().path());
        assertEquals(REPOSITORY, violations.getFirst().repositoryId());
    }

    @Test
    void validateLinks_pathsThatDifferOnlyInCase_isPathCaseConflict() {
        List<Violation> violations = rules.validateLinks(List.of(candidate(".agents/rules/java.md"),
                candidate(".agents/rules/Java.md")));
        assertEquals(List.of(ViolationRule.PATH_CASE_CONFLICT), violations.stream().map(Violation::rule).toList());
        assertEquals(".agents/rules/Java.md", violations.getFirst().path());
    }

    @Test
    void validate_projectFiles_mustKeepTheNamespaceOfTheirMount() {
        UUID projectSet = UUID.randomUUID();
        List<PathCandidate> files = List.of(file("AGENTS.md", 600, true),
                new PathCandidate("projects/kagi/AGENTS.md", false, null, 500, false, projectSet, null),
                new PathCandidate(".agents/rules/core-kagi.md", false, null, 10, false, projectSet, null));
        CompositionCandidate composition = new CompositionCandidate(REPOSITORY, files, List.of(), null,
                List.of(new MountCandidate(projectSet, "kagi", "projects/kagi")), List.of());

        List<Violation> violations = rules.validate(composition);

        assertEquals(List.of(ViolationRule.MOUNT_PATH_OUTSIDE_NAMESPACE), violations.stream().map(Violation::rule).toList());
        assertEquals(".agents/rules/core-kagi.md", violations.getFirst().path());
        assertEquals(REPOSITORY, violations.getFirst().repositoryId());
    }

    @Test
    void validate_guardrailOfAFolderProject_isGuardrailScopeAndOfThePackIsFine() {
        UUID kagiSet = UUID.randomUUID();
        UUID guardrailsSet = UUID.randomUUID();
        List<PathCandidate> files = List.of(
                file(".agents/rules/guardrails/protect-secrets.md", 100, true),
                new PathCandidate(".agents/rules/guardrails/kagi-only.md", false, null, 100, true, kagiSet, null),
                new PathCandidate(".agents/rules/guardrails/english.md", false, null, 100, true, guardrailsSet, null));
        CompositionCandidate composition = new CompositionCandidate(REPOSITORY, files, List.of(), null,
                List.of(new MountCandidate(kagiSet, "kagi", "projects/kagi"),
                        new MountCandidate(guardrailsSet, "guardrails", "projects/guardrails")),
                List.of());

        List<Violation> violations = rules.validate(composition);

        assertEquals(List.of(ViolationRule.GUARDRAIL_SCOPE, ViolationRule.GUARDRAIL_SCOPE),
                violations.stream().map(Violation::rule).toList());
        assertEquals(List.of(".agents/rules/guardrails/kagi-only.md", ".agents/rules/guardrails/english.md"),
                violations.stream().map(Violation::path).toList());
    }
}
