package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A guardrail deny rule must read as a permission rule exactly as given, since the
 * CLI merges the signed text as it is; allow and ask rules are never accepted.
 */
class PermissionRulesUnitTest {

    @Test
    void isParsable_toolNamesWithAndWithoutSpecifier_returnsTrue() {
        for (String rule : List.of("Bash", "Bash(curl:*)", "Read(./.env)", "WebFetch(domain:example.com)",
                "mcp__github__create_issue", "Bash(git commit -m \"a (b)\")")) {
            assertTrue(PermissionRules.isParsable(rule), rule);
        }
    }

    @Test
    void isParsable_malformedRules_returnsFalse() {
        for (String rule : List.of("", "bash", "Bash()", " Bash", "Bash ", "Bash(curl", "Bash(a)\nRead", "allow:Bash",
                "Bash(rm\t-rf)", "mcp__")) {
            assertFalse(PermissionRules.isParsable(rule), rule);
        }
    }

    @Test
    void findProblems_validDenyRules_returnsEmpty() {
        assertTrue(PermissionRules.findProblems(List.of("Bash(curl:*)", "WebFetch"), null, List.of()).isEmpty());
    }

    @Test
    void findProblems_allowAskDuplicateAndUnparsable_reportsEach() {
        List<Map<String, Object>> problems = PermissionRules.findProblems(
                List.of("Bash(rm:*)", "Bash(rm:*)", "nope"), List.of("Bash(curl:*)"), List.of("Read"));

        assertEquals(4, problems.size());
        assertEquals(Map.of("rule", "Bash(curl:*)", "problem", "NOT_DENY", "list", "allow"), problems.get(0));
        assertEquals(Map.of("rule", "Read", "problem", "NOT_DENY", "list", "ask"), problems.get(1));
        assertEquals(Map.of("rule", "Bash(rm:*)", "problem", "DUPLICATE"), problems.get(2));
        assertEquals(Map.of("rule", "nope", "problem", "UNPARSABLE"), problems.get(3));
    }
}
