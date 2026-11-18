package com.korur.oba.apps.kaya.render.service.impl;

// 1. Framework-level

// 2. Third-party
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.apps.kaya.guardrail.entity.Guardrail;
import com.korur.oba.apps.kaya.guardrail.enums.GuardrailCategory;
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.dto.embedded.FileRefusal;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.mock_data.RenderStore;
import com.korur.oba.apps.kaya.rule.entity.Rule;
import com.korur.oba.apps.kaya.rule.entity.RuleConvention;
import com.korur.oba.apps.kaya.skill.entity.Skill;
import com.korur.oba.apps.kaya.toolparameter.entity.ToolParameter;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;

import static com.korur.oba.apps.kaya.render.mock_data.RenderStore.utf8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderServiceImplUnitTest {

    private static final String RULE = ".agents/rules/10-backend.md";
    private static final String CONVENTION = "docs/private/conventions/backend/naming.md";
    private static final String OTHER_CONVENTION = "docs/private/conventions/backend/logging.md";
    private static final String SUBAGENT = ".claude/agents/reviewer.md";
    private static final String SHIP_WORKFLOW = ".agents/workflows/ship.md";
    private static final String MCP_FILE = ".mcp.json";
    private static final String GUARDRAIL = ".agents/rules/guardrails/protect-secrets.md";

    private RenderStore store;

    @BeforeEach
    void setUp() {
        store = new RenderStore();
    }

    @Test
    void parse_everyKind_rendersTheSameBytesAndModes() {
        // Arrange
        List<SourceFile> tree = tree();

        // Act
        store.renderService.parse(store.organizationId, store.versionId, tree);
        List<RenderedFile> rendered = render();

        // Assert
        assertSameTree(tree, rendered);
    }

    @Test
    void render_hookAndSettings_keepsExecutableModeAndSettingsBytes() {
        // Arrange
        List<SourceFile> tree = tree();
        store.renderService.parse(store.organizationId, store.versionId, tree);

        // Act
        Map<String, RenderedFile> rendered = byPath(render());

        // Assert
        assertEquals(FileMode.EXECUTABLE, rendered.get(".agents/hooks/guard.sh").mode());
        assertEquals(FileMode.FILE, rendered.get(".agents/hooks/README.md").mode());
        assertEquals(FileMode.EXECUTABLE, rendered.get(".agents/skills/kaizen/scripts/run.sh").mode());
        assertArrayEquals(settingsBytes(), rendered.get(".claude/settings.json").content());
        assertEquals(FileMode.SYMLINK, rendered.get(".claude/skills/kaizen").mode());
        assertEquals("../../.agents/skills/kaizen", rendered.get(".claude/skills/kaizen").symlinkTarget());
    }

    @Test
    void parse_guardrail_indexesItsFrontmatterAndRendersTheSameBytes() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, tree());

        // Act
        Map<String, RenderedFile> rendered = byPath(render());

        // Assert
        assertArrayEquals(guardrailBytes(), rendered.get(GUARDRAIL).content());
        assertEquals(1, store.guardrails.size());
        Guardrail guardrail = store.guardrails.get(0);
        assertEquals("protect-secrets", guardrail.getName());
        assertEquals(GuardrailCategory.SECURITY, guardrail.getCategory());
        assertEquals("Keep secrets out of sessions", guardrail.getDescription());
        assertEquals(List.of("Read(./.env)", "Read(./secrets/**)"), guardrail.getDeny());
        assertTrue(store.rules.stream().noneMatch(rule -> rule.getRelativePath().contains("guardrails")));
    }

    @Test
    void parseOne_invalidGuardrail_throwsInvalidFileAndStoresNothing() {
        // Arrange
        String head = "---\ncategory: SECURITY\n";
        Map<SourceFile, String> files = Map.of(
                SourceFile.of(GUARDRAIL, FileMode.FILE, utf8("---\ncategory: SPEED\n---\nBe fast.\n")),
                "the category SPEED is not one of [SECURITY, LANGUAGE, QUALITY, DATA, OTHER]",
                SourceFile.of(GUARDRAIL, FileMode.FILE, utf8(head + "paths:\n  - \"src/**\"\n---\nText.\n")),
                String.format(RenderConstant.GUARDRAIL_KEY_UNKNOWN_REASON, "paths"),
                SourceFile.of(GUARDRAIL, FileMode.FILE, utf8(head + "---\n  \n")),
                RenderConstant.GUARDRAIL_BODY_EMPTY_REASON,
                SourceFile.of(GUARDRAIL, FileMode.FILE, utf8(head + "---\n" + "x".repeat(4001) + "\n")),
                "the instruction text has 4001 characters; a guardrail holds at most 4000",
                SourceFile.of(GUARDRAIL, FileMode.FILE, utf8(head + "deny:\n" + "  - Read(./a)\n".repeat(21) + "---\nText.\n")),
                "the guardrail has 21 deny rules; it holds at most 20",
                SourceFile.of(GUARDRAIL, FileMode.FILE, utf8(head + "deny:\n  - Read(./a)\n  - Read(./a)\n---\nText.\n")),
                "the deny rule Read(./a) is refused: " + ValidationConstant.RULE_PROBLEM_DUPLICATE,
                SourceFile.of(".agents/rules/guardrails/Bad_Name.md", FileMode.FILE, utf8(head + "---\nText.\n")),
                RenderConstant.GUARDRAIL_NAME_REASON);

        // Act and Assert
        files.forEach((file, reason) -> {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> store.renderService.parseOne(store.organizationId, store.versionId, file));
            assertEquals(ExceptionDomainKayaError.RENDER_INVALID_FILE.getCode(), exception.getErrorCode(), reason);
            assertEquals(reason, exception.getErrorValues().get(RenderConstant.FIELD_REASON));
        });
        assertTrue(store.guardrails.isEmpty());
    }

    @Test
    void parseOne_guardrailSavedAndRemoved_replacesThenDeletesItsRow() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, tree());
        SourceFile saved = SourceFile.of(GUARDRAIL, FileMode.FILE, utf8("---\ncategory: DATA\n---\nNo customer data.\n"));

        // Act
        store.renderService.parseOne(store.organizationId, store.versionId, saved);
        List<String> savedDeny = List.copyOf(store.guardrails.get(0).getDeny());
        GuardrailCategory savedCategory = store.guardrails.get(0).getCategory();
        int savedRows = store.guardrails.size();
        store.renderService.parseOne(store.organizationId, store.versionId, SourceFile.removal(GUARDRAIL));

        // Assert
        assertEquals(1, savedRows);
        assertEquals(GuardrailCategory.DATA, savedCategory);
        assertEquals(List.of(), savedDeny);
        assertTrue(store.guardrails.isEmpty());
        assertNull(byPath(render()).get(GUARDRAIL));
    }

    @Test
    void parse_mcpFile_throwsNotDeliverableAndStoresNothing() {
        // Arrange
        List<SourceFile> tree = new ArrayList<>(tree());
        tree.add(SourceFile.of(MCP_FILE, FileMode.FILE, utf8("{ \"mcpServers\": {} }\n")));

        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> store.renderService.parse(store.organizationId, store.versionId, tree));

        // Assert
        assertEquals(ExceptionDomainKayaError.RENDER_NOT_DELIVERABLE.getCode(), exception.getErrorCode());
        assertEquals(MCP_FILE, exception.getErrorValues().get(RenderConstant.FIELD_PATH));
        assertTrue(store.rowCounts().stream().allMatch(count -> count == 0));
        assertTrue(store.blobs.isEmpty());
    }

    @Test
    void screen_mcpFile_refusesOnlyThatFileAsNotDeliverable() {
        // Arrange
        List<SourceFile> files = List.of(
                SourceFile.of(MCP_FILE, FileMode.FILE, utf8("{ \"mcpServers\": {} }\n")),
                SourceFile.of(RULE, FileMode.FILE, utf8("# Backend\n")));

        // Act
        List<FileRefusal> refusals = store.renderQueryService.screen(files);

        // Assert
        assertEquals(1, refusals.size());
        assertEquals(MCP_FILE, refusals.get(0).path());
        assertEquals(ExceptionDomainKayaError.RENDER_NOT_DELIVERABLE.getCode(), refusals.get(0).errorCode());
    }

    @Test
    void parse_shadcnAndNextBestPracticesFixtures_mapsEveryPartAndRendersTheSameBytes() {
        // Arrange
        List<SourceFile> tree = new ArrayList<>();
        tree.addAll(fixtureSkill("shadcn"));
        tree.addAll(fixtureSkill("next-best-practices"));

        // Act
        store.renderService.parse(store.organizationId, store.versionId, tree);
        List<RenderedFile> rendered = render();

        // Assert
        assertSameTree(tree, rendered);
        assertEquals(2, store.skills.size());
        Skill shadcn = skill("shadcn");
        assertEquals(5, shadcn.getConventions().size(), "rules/*.md are conventions");
        assertEquals(2, shadcn.getToolParameters().size(), "evals.json and agents/openai.yml are tool parameters");
        assertEquals(5, shadcn.getReferences().size(), "the two PNGs and three Markdown pages are references");
        assertTrue(store.references.stream().anyMatch(reference -> "image/png".equals(reference.getMediaType())));
        assertTrue(store.conventions.stream().allMatch(convention -> convention.getRelativePath() == null));
        Skill next = skill("next-best-practices");
        assertEquals(19, next.getReferences().size());
        assertTrue(store.toolParameters.stream().map(ToolParameter::getName).toList().contains("evals.json"));
    }

    @Test
    void parse_identicalPartInTwoSkills_storesOneRowAndRendersItIntoBoth() {
        // Arrange
        byte[] shared = utf8("# Shared\n\nOne text for both skills.\n");
        List<SourceFile> tree = List.of(
                skillFile("alpha"), SourceFile.of(".agents/skills/alpha/references/shared.md", FileMode.FILE, shared),
                skillFile("beta"), SourceFile.of(".agents/skills/beta/references/shared.md", FileMode.FILE, shared));

        // Act
        store.renderService.parse(store.organizationId, store.versionId, tree);
        Map<String, RenderedFile> rendered = byPath(render());

        // Assert
        assertEquals(1, store.references.size());
        assertArrayEquals(shared, rendered.get(".agents/skills/alpha/references/shared.md").content());
        assertArrayEquals(shared, rendered.get(".agents/skills/beta/references/shared.md").content());
    }

    @Test
    void parseOne_sharedPartEditedInOneSkill_changesItInBoth() {
        // Arrange
        byte[] shared = utf8("# Shared\n");
        store.renderService.parse(store.organizationId, store.versionId, List.of(
                skillFile("alpha"), SourceFile.of(".agents/skills/alpha/references/shared.md", FileMode.FILE, shared),
                skillFile("beta"), SourceFile.of(".agents/skills/beta/references/shared.md", FileMode.FILE, shared)));
        byte[] edited = utf8("# Shared\n\nEdited once.\n");

        // Act
        store.renderService.parseOne(store.organizationId, store.versionId,
                SourceFile.of(".agents/skills/alpha/references/shared.md", FileMode.FILE, edited));
        Map<String, RenderedFile> rendered = byPath(render());

        // Assert
        assertEquals(1, store.references.size());
        assertArrayEquals(edited, rendered.get(".agents/skills/alpha/references/shared.md").content());
        assertArrayEquals(edited, rendered.get(".agents/skills/beta/references/shared.md").content());
    }

    @Test
    void parseOne_ruleSavedWithOtherLink_replacesItsConventionLinks() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, tree());
        assertEquals(List.of(CONVENTION), conventionLinks());
        byte[] edited = utf8("---\ndescription: Backend rules\n---\n\nSee [logging](../../" + OTHER_CONVENTION + ").\n");

        // Act
        store.renderService.parseOne(store.organizationId, store.versionId, SourceFile.of(RULE, FileMode.FILE, edited));

        // Assert
        assertEquals(List.of(OTHER_CONVENTION), conventionLinks());
        assertArrayEquals(edited, byPath(render()).get(RULE).content());
    }

    @Test
    void parse_workflowWithChecksKey_storesWorkflowWithoutIndexingChecks() {
        // Act
        store.renderService.parse(store.organizationId, store.versionId, tree());

        // Assert
        Workflow ship = store.workflows.stream().filter(row -> "ship.md".equals(row.getRelativePath())).findFirst().orElseThrow();
        assertEquals("Ship", ship.getTitle());
    }

    @Test
    void parseOne_pipelineFile_throwsNotDeliverableAndStoresNothing() {
        // Arrange
        SourceFile pipeline = SourceFile.of(".agents/pipelines/feature.yaml", FileMode.FILE,
                utf8("name: feature\nbranches: [feat/*]\nsteps:\n  - workflow: ship\n"));

        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> store.renderService.parseOne(store.organizationId, store.versionId, pipeline));

        // Assert
        assertEquals(ExceptionDomainKayaError.RENDER_NOT_DELIVERABLE.getCode(), exception.getErrorCode());
        assertTrue(store.blobs.isEmpty());
    }

    @Test
    void parseOne_subagentWithZeroMaxTurns_throwsInvalidFileAndKeepsStoredData() {
        // Arrange
        store.renderService.parse(store.organizationId, store.versionId, tree());
        List<RenderedFile> before = render();
        List<Integer> counts = store.rowCounts();
        SourceFile invalid = SourceFile.of(SUBAGENT, FileMode.FILE, utf8("---\nname: reviewer\nmaxTurns: 0\n---\nBody\n"));

        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> store.renderService.parseOne(store.organizationId, store.versionId, invalid));

        // Assert
        assertEquals(ExceptionDomainKayaError.RENDER_INVALID_FILE.getCode(), exception.getErrorCode());
        assertEquals(counts, store.rowCounts());
        assertSameTree(before.stream().map(RenderedFile::toSource).toList(), render());
    }

    @Test
    void parse_duplicateSubagentName_throwsInvalidFileAndStoresNothing() {
        // Arrange
        List<SourceFile> tree = new ArrayList<>(tree());
        tree.add(SourceFile.of(".claude/agents/copy.md", FileMode.FILE, utf8("---\nname: reviewer\n---\nCopy\n")));

        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> store.renderService.parse(store.organizationId, store.versionId, tree));

        // Assert
        assertEquals(ExceptionDomainKayaError.RENDER_INVALID_FILE.getCode(), exception.getErrorCode());
        assertTrue(store.rowCounts().stream().allMatch(count -> count == 0));
        assertTrue(store.blobs.isEmpty());
    }

    @Test
    void parseOne_ruleWithBrokenFrontmatter_throwsInvalidFile() {
        // Arrange
        SourceFile invalid = SourceFile.of(RULE, FileMode.FILE, utf8("---\npaths: [unclosed\n---\nBody\n"));

        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> store.renderService.parseOne(store.organizationId, store.versionId, invalid));

        // Assert
        assertEquals(ExceptionDomainKayaError.RENDER_INVALID_FILE.getCode(), exception.getErrorCode());
        assertTrue(store.rules.isEmpty());
    }

    @Test
    void parseOne_pathNoKindAccepts_throwsNotDeliverable() {
        // Arrange
        List<SourceFile> files = List.of(
                SourceFile.of("src/Main.java", FileMode.FILE, utf8("class Main {}\n")),
                SourceFile.of(".agents/rules/notes.txt", FileMode.FILE, utf8("notes\n")));

        // Act and Assert
        for (SourceFile file : files) {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> store.renderService.parseOne(store.organizationId, store.versionId, file));
            assertEquals(ExceptionDomainKayaError.RENDER_NOT_DELIVERABLE.getCode(), exception.getErrorCode(), file.path());
        }
        assertTrue(store.blobs.isEmpty());
    }

    @Test
    void parse_treeWithUndeliverablePath_throwsNotDeliverableAndStoresNothing() {
        // Arrange
        List<SourceFile> tree = new ArrayList<>(tree());
        tree.add(SourceFile.of("README.md", FileMode.FILE, utf8("# Readme\n")));

        // Act
        ValidationException exception = assertThrows(ValidationException.class,
                () -> store.renderService.parse(store.organizationId, store.versionId, tree));

        // Assert
        assertEquals(ExceptionDomainKayaError.RENDER_NOT_DELIVERABLE.getCode(), exception.getErrorCode());
        assertTrue(store.rowCounts().stream().allMatch(count -> count == 0));
    }

    private List<SourceFile> tree() {
        return List.of(
                SourceFile.of("AGENTS.md", FileMode.FILE, utf8("# Agents\n\nRead the rules.\n")),
                SourceFile.of(".agents/AGENTS.md", FileMode.FILE, utf8("# Agent tools\n")),
                SourceFile.of(RULE, FileMode.FILE, utf8("---\n# the scope of this rule\npaths:\n  - \"src/**\"  # sources\n"
                        + "description: Backend rules\n---\n\nSee [naming](../../" + CONVENTION + ").\n")),
                SourceFile.of(GUARDRAIL, FileMode.FILE, guardrailBytes()),
                SourceFile.of(CONVENTION, FileMode.FILE, utf8("# Naming\n\nNames are nouns.\n")),
                SourceFile.of(OTHER_CONVENTION, FileMode.FILE, utf8("# Logging\n")),
                SourceFile.of(".agents/roles/coder.md", FileMode.FILE, utf8("---\ntitle: Coder\n---\nWrite code.\n")),
                SourceFile.of(".agents/workflows/implement.md", FileMode.FILE, utf8("# Implement\n\nSteps.\n")),
                SourceFile.of(SHIP_WORKFLOW, FileMode.FILE, utf8("---\nchecks:\n  - kind: COMMAND\n    match: mvn test\n"
                        + "  - kind: PULL_REQUEST\n---\n# Ship\n")),
                SourceFile.of(".agents/templates/HANDOFF.md", FileMode.FILE, utf8("# Handoff\n")),
                SourceFile.of(".agents/hooks/guard.sh", FileMode.EXECUTABLE, utf8("#!/bin/sh\nexit 0\n")),
                SourceFile.of(".agents/hooks/README.md", FileMode.FILE, utf8("# Hooks\n")),
                SourceFile.of(SUBAGENT, FileMode.FILE, utf8("---\ntools: Read, Grep\nmaxTurns: 5\nname: reviewer\n"
                        + "description: Reviews code\n---\nReview.\n")),
                SourceFile.of(".claude/settings.json", FileMode.FILE, settingsBytes()),
                skillFile("kaizen"),
                SourceFile.of(".agents/skills/kaizen/scripts/run.sh", FileMode.EXECUTABLE, utf8("#!/bin/sh\necho run\n")),
                SourceFile.of(".agents/skills/kaizen/workflows/close.md", FileMode.FILE, utf8("# Close a ticket\n")),
                new SourceFile(".claude/skills/kaizen", FileMode.SYMLINK, "../../.agents/skills/kaizen", null, null));
    }

    // A comment and odd spacing in the frontmatter, so a byte-exact render is told apart from a re-serialised one.
    private static byte[] guardrailBytes() {
        return utf8("---\ncategory: SECURITY   # what it protects\ndescription: Keep secrets out of sessions\n"
                + "deny:\n  - \"Read(./.env)\"\n  - Read(./secrets/**)\n---\n\nNever read or print secret files.\n");
    }

    private static byte[] settingsBytes() {
        return utf8("{\n    \"permissions\" : { \"allow\": [ \"Bash(ls)\" ] },\n  \"env\":{}\n}\n");
    }

    private static SourceFile skillFile(String name) {
        return SourceFile.of(".agents/skills/" + name + "/SKILL.md", FileMode.FILE,
                utf8("---\nname: " + name + "\ndescription: The " + name + " skill\n---\n\n# " + name + "\n"));
    }

    private static List<SourceFile> fixtureSkill(String name) {
        try {
            URL url = RenderServiceImplUnitTest.class.getResource("/render/skills/" + name);
            Path root = Path.of(url.toURI());
            try (Stream<Path> paths = Files.walk(root)) {
                List<SourceFile> files = paths.filter(Files::isRegularFile)
                        .sorted(Comparator.naturalOrder())
                        .map(path -> SourceFile.of(".agents/skills/" + name + "/" + root.relativize(path).toString().replace('\\', '/'),
                                FileMode.FILE, read(path)))
                        .collect(Collectors.toCollection(ArrayList::new));
                files.add(new SourceFile(".claude/skills/" + name, FileMode.SYMLINK, "../../.agents/skills/" + name, null, null));
                return files;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<RenderedFile> render() {
        return store.renderQueryService.render(store.organizationId, store.versionId);
    }

    private Skill skill(String name) {
        return store.skills.stream().filter(skill -> skill.getName().equals(name)).findFirst().orElseThrow();
    }

    private List<String> conventionLinks() {
        Rule rule = store.rules.stream().filter(row -> RULE.endsWith(row.getRelativePath())).findFirst().orElseThrow();
        return rule.getConventions().stream().map(RuleConvention::getConventionPath).toList();
    }

    private static Map<String, RenderedFile> byPath(List<RenderedFile> files) {
        return files.stream().collect(Collectors.toMap(RenderedFile::path, file -> file));
    }

    private static void assertSameTree(List<SourceFile> expected, List<RenderedFile> actual) {
        Map<String, RenderedFile> rendered = byPath(actual);
        Set<String> expectedPaths = expected.stream().map(SourceFile::path).collect(Collectors.toSet());
        assertEquals(expectedPaths, rendered.keySet());
        for (SourceFile file : expected) {
            RenderedFile out = rendered.get(file.path());
            assertEquals(file.mode(), out.mode(), file.path());
            assertEquals(file.symlinkTarget(), out.symlinkTarget(), file.path());
            assertTrue(Arrays.equals(file.content(), out.content()), file.path());
        }
    }
}
