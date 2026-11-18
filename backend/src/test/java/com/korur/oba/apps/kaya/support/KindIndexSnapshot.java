package com.korur.oba.apps.kaya.support;

// 1. Framework-level
import org.springframework.jdbc.core.JdbcTemplate;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A text dump of every index row a version owns: the kind tables, the rule-to-convention links and
 * the skill-to-part links. The exact dump keeps ids and timestamps, so it proves rows were not
 * rewritten; the content dump leaves them out, so two versions with the same files compare equal.
 */
public final class KindIndexSnapshot {

    private static final List<String> KIND_TABLES = List.of(
            "kaya.agents_files", "kaya.rules", "kaya.conventions", "kaya.roles", "kaya.workflows",
            "kaya.templates", "kaya.hooks", "kaya.subagents", "kaya.settings",
            "kaya.scripts", "kaya.reference_files", "kaya.tool_parameters", "kaya.skills");

    private static final List<String> SKILL_LINKS = List.of(
            "kaya.skill_workflows", "kaya.skill_scripts", "kaya.skill_conventions", "kaya.skill_references",
            "kaya.skill_tool_parameters");

    private KindIndexSnapshot() {
    }

    /** Every row with its ids and timestamps, as stored. */
    public static List<String> exact(JdbcTemplate jdbc, UUID versionId) {
        return dump(jdbc, versionId, false);
    }

    /** Every row without ids, version and timestamps; the links read as owner and path. */
    public static List<String> content(JdbcTemplate jdbc, UUID versionId) {
        return dump(jdbc, versionId, true);
    }

    private static List<String> dump(JdbcTemplate jdbc, UUID versionId, boolean content) {
        String strip = content ? " - '{id,version_id,created_at,updated_at}'::text[]" : "";
        List<String> lines = new ArrayList<>();
        for (String table : KIND_TABLES) {
            for (String row : jdbc.queryForList("SELECT (to_jsonb(t)" + strip + ")::text FROM " + table
                    + " t WHERE t.version_id = ? ORDER BY 1", String.class, versionId)) {
                lines.add(table + " " + row);
            }
        }
        String rule = content ? "r.relative_path || ' > ' || l.convention_path" : "to_jsonb(l)::text";
        for (String row : jdbc.queryForList("SELECT " + rule + " FROM kaya.rule_conventions l JOIN kaya.rules r ON r.id = l.rule_id "
                + "WHERE r.version_id = ? ORDER BY 1", String.class, versionId)) {
            lines.add("kaya.rule_conventions " + row);
        }
        for (String link : SKILL_LINKS) {
            String value = content ? "s.name || ' ' || l.path" : "to_jsonb(l)::text";
            for (String row : jdbc.queryForList("SELECT " + value + " FROM " + link + " l JOIN kaya.skills s ON s.id = l.skill_id "
                    + "WHERE s.version_id = ? ORDER BY 1", String.class, versionId)) {
                lines.add(link + " " + row);
            }
        }
        return lines;
    }
}
