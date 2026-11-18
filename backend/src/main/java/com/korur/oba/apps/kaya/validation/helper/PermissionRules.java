package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;

/**
 * Checks Claude Code permission rules that Kaya signs into a delivery: the deny rules of a
 * guardrail. Every rule must read as the allowlist reads a permission rule (a
 * tool name, optionally a specifier in parentheses), exactly as given: the CLI merges the signed text
 * into permissions.deny byte for byte, so a rule Claude Code would read differently, or not at all,
 * would deny nothing while the issuer believes it does. A rule given in allow or ask is refused,
 * because both only ever take rights away.
 *
 * Used by the render domain when it parses a guardrail.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PermissionRules {

    private static final Pattern RULE = Pattern.compile(ValidationConstant.RULE_PATTERN, Pattern.DOTALL);

    private PermissionRules() {
    }

    /**
     * Lists every refused rule with the reason it was refused.
     *
     * @param deny  the deny rules, not null
     * @param allow rules sent as allow, may be null
     * @param ask   rules sent as ask, may be null; a guardrail passes null for both
     * @return one entry per refused rule ({rule, problem, list}); empty when all are accepted
     */
    public static List<Map<String, Object>> findProblems(List<String> deny, List<String> allow, List<String> ask) {
        List<Map<String, Object>> problems = new ArrayList<>();
        notDeny(problems, allow, ValidationConstant.LIST_ALLOW);
        notDeny(problems, ask, ValidationConstant.LIST_ASK);
        Set<String> seen = new HashSet<>();
        for (String rule : deny) {
            if (!isParsable(rule)) {
                problems.add(problem(rule, ValidationConstant.RULE_PROBLEM_UNPARSABLE, null));
            } else if (!seen.add(rule)) {
                problems.add(problem(rule, ValidationConstant.RULE_PROBLEM_DUPLICATE, null));
            }
        }
        return problems;
    }

    /**
     * Whether a rule reads as a permission rule exactly as given: no surrounding whitespace, no
     * line break or other control character, a tool name and, in parentheses, a non-empty
     * specifier.
     *
     * @param rule the rule
     * @return true when it parses
     */
    static boolean isParsable(String rule) {
        if (rule == null || rule.isEmpty() || !rule.equals(rule.strip())) {
            return false;
        }
        for (int index = 0; index < rule.length(); index++) {
            if (Character.isISOControl(rule.charAt(index))) {
                return false;
            }
        }
        return RULE.matcher(rule).matches();
    }

    private static void notDeny(List<Map<String, Object>> problems, List<String> rules, String list) {
        if (rules == null) {
            return;
        }
        for (String rule : rules) {
            problems.add(problem(rule, ValidationConstant.RULE_PROBLEM_NOT_DENY, list));
        }
    }

    private static Map<String, Object> problem(String rule, String problem, String list) {
        // LinkedHashMap, not Map.of: a null rule from the request must still be reported.
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(ValidationConstant.FIELD_RULE, rule);
        entry.put(ValidationConstant.FIELD_PROBLEM, problem);
        if (list != null) {
            entry.put(ValidationConstant.FIELD_LIST, list);
        }
        return entry;
    }
}
