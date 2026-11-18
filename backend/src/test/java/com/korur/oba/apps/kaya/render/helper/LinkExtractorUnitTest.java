package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;

// 4. Internal

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkExtractorUnitTest {

    private static final String RULE = ".agents/rules/11-backend-rules.md";
    private static final String DOC = "docs/private/conventions/backend/03-service-cqrs-convention.md";

    @Test
    void extract_realBackendRule_findsItsConventionDocAndRuleLinks() throws IOException {
        String markdown = resource("config/links/11-backend-rules.md");

        List<LinkCandidate> links = LinkExtractor.extract(RULE, markdown);

        assertEquals(List.of(
                new LinkCandidate(RULE, ".agents/rules/09-skill-routing.md", 8),
                new LinkCandidate(RULE, "docs/private/conventions/backend/32-soft-delete-convention.md", 14)), links);
    }

    @Test
    void extract_markdownLinks_resolveFromTheSourceDirectoryOrTheRoot() {
        String markdown = String.join("\n",
                "See [soft delete](./32-soft-delete-convention.md) and [the index](/docs/private/conventions/_index.md).",
                "Also [frontend](../frontend/01-x.md#forms) and [query](20-entity-convention.md?plain=1).");

        List<LinkCandidate> links = LinkExtractor.extract(DOC, markdown);

        assertEquals(List.of(
                new LinkCandidate(DOC, "docs/private/conventions/backend/32-soft-delete-convention.md", 1),
                new LinkCandidate(DOC, "docs/private/conventions/_index.md", 1),
                new LinkCandidate(DOC, "docs/private/conventions/frontend/01-x.md", 2),
                new LinkCandidate(DOC, "docs/private/conventions/backend/20-entity-convention.md", 2)), links);
    }

    @Test
    void extract_codeSpanPaths_resolveFromTheRoot() {
        String markdown = "Read `docs/private/conventions/backend/` and `./.agents/rules/core.md`, not `./mvnw`, `...`, "
                + "`testing-patterns` or `mvn clean package`.";

        List<LinkCandidate> links = LinkExtractor.extract(RULE, markdown);

        assertEquals(List.of(
                new LinkCandidate(RULE, "docs/private/conventions/backend/", 1),
                new LinkCandidate(RULE, ".agents/rules/core.md", 1)), links);
    }

    @Test
    void extract_codeSpanSiblingDoc_resolvesFromTheSourceDirectoryWhenTheRootDeliversNothing() {
        String index = "docs/private/conventions/backend/_index.md";
        String markdown = "Read `03-service-cqrs-convention.md`, `../frontend/` and `AGENTS.md`.";

        List<LinkCandidate> links = LinkExtractor.extract(index, markdown);

        assertEquals(List.of(
                new LinkCandidate(index, DOC, 1),
                new LinkCandidate(index, "docs/private/conventions/frontend/", 1),
                new LinkCandidate(index, "AGENTS.md", 1)), links);
    }

    @Test
    void extract_codeSpanCommandInARule_neverResolvesBesideTheRule() {
        List<LinkCandidate> links = LinkExtractor.extract(RULE, "Do not run `./mvnw` or `mvnw`.");

        assertTrue(links.isEmpty(), () -> "expected no links, got " + links);
    }

    @Test
    void extract_codeSpanRepoRootPathsInARule_neverResolveBesideTheRule() {
        String registry = ".agents/rules/00-project-registry.md";
        String markdown = "CI lives in `.github/workflows/`; see `projects/kagi/operator/AGENTS.md` and `SKILL.md`.";

        List<LinkCandidate> links = LinkExtractor.extract(registry, markdown);

        // A folder's AGENTS.md is extracted from the root; whether it is listed depends on delivery.
        assertEquals(List.of("projects/kagi/operator/AGENTS.md"), links.stream().map(LinkCandidate::targetPath).toList());
    }

    @Test
    void extract_codeSpanDirectoryInASkill_neverResolvesBesideTheSkill() {
        List<LinkCandidate> links = LinkExtractor.extract(".agents/skills/shadcn/cli.md", "Components go in `src/`.");

        assertTrue(links.isEmpty(), () -> "expected no links, got " + links);
    }

    @Test
    void extract_codeSpanNestedPathInAConventionDoc_neverResolvesBesideTheDoc() {
        List<LinkCandidate> links = LinkExtractor.extract(DOC, "See `.claude/rules/10-no-silent-failures.md`.");

        assertTrue(links.isEmpty(), () -> "expected no links, got " + links);
    }

    @Test
    void extract_urlsUndeliveredPathsFencesAndSelfLinks_areLeftOut() {
        String markdown = String.join("\n",
                "[site](https://sensey.io/docs/a.md) [mail](mailto:dev@example.com) [anchor](#tests) [self](./11-backend-rules.md)",
                "[kaizen docs](/docs/private/projects/kaizen/) `projects/kaya/backend` `.agents/skills/<name>/SKILL.md`",
                "[up](../../../../etc/passwd.md)",
                "```markdown",
                "[inside a fence](/docs/private/conventions/a.md)",
                "```",
                "`[a link in code](/docs/private/conventions/b.md)`");

        List<LinkCandidate> links = LinkExtractor.extract(RULE, markdown);

        assertTrue(links.isEmpty(), () -> "expected no links, got " + links);
    }

    @Test
    void extract_referenceDefinition_isALink() {
        String markdown = "[soft]: /docs/private/conventions/backend/32-soft-delete-convention.md \"Soft delete\"";

        List<LinkCandidate> links = LinkExtractor.extract(RULE, markdown);

        assertEquals(List.of(new LinkCandidate(RULE, "docs/private/conventions/backend/32-soft-delete-convention.md", 1)),
                links);
    }

    @Test
    void extractAll_sourceNotUtf8_hasNoLinks() {
        Map<String, byte[]> contents = new LinkedHashMap<>();
        contents.put(RULE, "`AGENTS.md`".getBytes(StandardCharsets.UTF_8));
        contents.put(DOC, new byte[]{(byte) 0xC3, (byte) 0x28});

        List<LinkCandidate> links = LinkExtractor.extractAll(contents);

        assertEquals(List.of(new LinkCandidate(RULE, "AGENTS.md", 1)), links);
    }

    @Test
    void isDelivered_deliveredFileOrDirectory_isTrueAndOtherwiseFalse() {
        NavigableSet<String> delivered = new TreeSet<>(List.of(
                "docs/private/conventions/backend/32-soft-delete-convention.md", ".agents/skills/kaizen/SKILL.md"));

        assertTrue(LinkExtractor.isDelivered("docs/private/conventions/backend/32-soft-delete-convention.md", delivered));
        assertTrue(LinkExtractor.isDelivered("docs/private/conventions/backend/", delivered));
        assertTrue(LinkExtractor.isDelivered(".agents/skills/kaizen", delivered));
        assertFalse(LinkExtractor.isDelivered("docs/private/conventions/frontend/", delivered));
        assertFalse(LinkExtractor.isDelivered(".agents/skills/kai", delivered));
    }

    @Test
    void isListed_folderAgentsFile_onlyWhenDelivered() {
        String nested = "projects/kagi/operator/AGENTS.md";

        assertTrue(LinkExtractor.isListed(nested, true));
        assertFalse(LinkExtractor.isListed(nested, false));
        assertTrue(LinkExtractor.isListed("AGENTS.md", false));
        assertTrue(LinkExtractor.isListed(".agents/AGENTS.md", false));
        assertTrue(LinkExtractor.isListed("docs/private/conventions/AGENTS.md", false));
        assertTrue(LinkExtractor.isListed(".agents/rules/x.md", false));
    }

    private static String resource(String name) throws IOException {
        try (InputStream stream = Objects.requireNonNull(
                LinkExtractorUnitTest.class.getClassLoader().getResourceAsStream(name), name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
