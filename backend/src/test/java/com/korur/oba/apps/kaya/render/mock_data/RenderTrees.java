package com.korur.oba.apps.kaya.render.mock_data;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;

/**
 * File trees that hold every kind a version stores, with frontmatter written the way people write
 * it: comments, keys out of the canonical order, single and double quotes. The base tree is what a
 * version holds before a save; the edited tree changes, adds and drops files of several kinds.
 */
public final class RenderTrees {

    public static final String SETTINGS_PATH = ".claude/settings.json";

    private RenderTrees() {
    }

    private static byte[] utf8(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static SourceFile file(String path, String content) {
        return SourceFile.of(path, FileMode.FILE, utf8(content));
    }

    private static SourceFile executable(String path, String content) {
        return SourceFile.of(path, FileMode.EXECUTABLE, utf8(content));
    }

    /** Every kind once, with a skill that owns a script, a workflow, a convention, a reference and a tool parameter. */
    public static List<SourceFile> base() {
        List<SourceFile> tree = new ArrayList<>();
        tree.add(file("AGENTS.md", "# Agents\n\nRead the rules.\n"));
        tree.add(file(".agents/AGENTS.md", "# Agent tools\n"));
        tree.add(file(".agents/rules/10-backend.md", "---\n# the scope of this rule\npaths:\n  - 'src/**'  # sources\n"
                + "  - \"docs/**\"\ndescription: \"Backend rules\"\n---\n\nSee [naming](../../docs/private/conventions/backend/naming.md).\n"));
        tree.add(file(".agents/rules/00-core.md", "Core rule for every file.\n"));
        tree.add(file("docs/private/conventions/backend/naming.md", "# Naming\n\nNames are nouns.\n"));
        tree.add(file("docs/private/conventions/backend/logging.md", "# Logging\n"));
        tree.add(file(".agents/roles/coder.md", "---\ntitle: 'Coder'\n---\nWrite code.\n"));
        tree.add(file(".agents/roles/tester.md", "# Tester\n\nWrite tests.\n"));
        tree.add(file(".agents/workflows/implement.md", "# Implement\n\nSteps.\n"));
        tree.add(file(".agents/templates/HANDOFF.md", "# Handoff\n"));
        tree.add(executable(".agents/hooks/guard.sh", "#!/bin/sh\nexit 0\n"));
        tree.add(file(".agents/hooks/README.md", "# Hooks\n"));
        tree.add(file(".claude/agents/reviewer.md", "---\ntools: Read, Grep\nmaxTurns: 5\nname: reviewer\n"
                + "description: 'Reviews code'\n---\nReview.\n"));
        tree.add(file(SETTINGS_PATH, "{\n    \"permissions\" : { \"allow\": [ \"Bash(ls)\" ] },\n  \"env\":{}\n}\n"));
        tree.add(file(".agents/skills/kaizen/SKILL.md", "---\nname: kaizen\ndescription: The kaizen skill\n---\n\n# kaizen\n"));
        tree.add(executable(".agents/skills/kaizen/scripts/run.sh", "#!/bin/sh\necho run\n"));
        tree.add(file(".agents/skills/kaizen/workflows/close.md", "# Close a ticket\n"));
        tree.add(file(".agents/skills/kaizen/conventions/style.md", "# Style\n"));
        tree.add(file(".agents/skills/kaizen/references/api.md", "# API\n"));
        tree.add(file(".agents/skills/kaizen/agents/openai.yml", "interface:\n  display_name: Kaizen\n"));
        tree.add(new SourceFile(".claude/skills/kaizen", FileMode.SYMLINK, "../../.agents/skills/kaizen", null, null));
        return tree;
    }

    /** The base tree with a changed rule, role and skill part, a new template, and the hook, tester role and settings dropped or changed. */
    public static List<SourceFile> edited() {
        List<SourceFile> tree = new ArrayList<>();
        for (SourceFile source : base()) {
            switch (source.path()) {
                case ".agents/rules/10-backend.md" -> tree.add(file(source.path(), "---\npaths: \"src/**\"\n---\n\n"
                        + "See [logging](../../docs/private/conventions/backend/logging.md).\n"));
                case ".agents/roles/tester.md", ".agents/hooks/guard.sh" -> {
                }
                case ".agents/skills/kaizen/references/api.md" -> tree.add(file(source.path(), "# API v2\n"));
                case SETTINGS_PATH -> tree.add(file(source.path(), "{\"permissions\":{\"allow\":[\"Bash(pwd)\"]}}\n"));
                default -> tree.add(source);
            }
        }
        tree.add(file(".agents/templates/PLAN.md", "# Plan\n"));
        tree.add(file(".agents/skills/kaizen/references/extra.md", "# Extra\n"));
        return tree;
    }
}
