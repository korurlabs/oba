package com.korur.oba.apps.kaya.release.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kaya.render.helper.Frontmatter;
import com.korur.oba.apps.kaya.render.helper.FrontmatterParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleNarrowerUnitTest {

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String narrowed(String rule, List<String> kept) {
        return new String(RuleNarrower.narrow(bytes(rule), kept).orElseThrow(), StandardCharsets.UTF_8);
    }

    @Test
    void narrow_inlineList_rewritesOnlyThePathsLine() {
        String rule = "---\ndescription: Kagi API\npaths: [\"projects/kagi/**\", \"projects/kaizen/**\"]\n---\n# Body\n";
        assertEquals("---\ndescription: Kagi API\npaths: [\"projects/kaizen/**\"]\n---\n# Body\n",
                narrowed(rule, List.of("projects/kaizen/**")));
    }

    @Test
    void narrow_blockList_replacesItsItemLines() {
        String rule = "---\npaths:\n  - projects/kagi/**\n\n  - projects/kaizen/**\n\nname: x\n---\nBody\n";
        assertEquals("---\npaths: [\"projects/kaizen/**\"]\n\nname: x\n---\nBody\n", narrowed(rule, List.of("projects/kaizen/**")));
    }

    @Test
    void narrow_crlfFile_keepsItsLineEndings() {
        String rule = "---\r\npaths: projects/kagi/**, projects/kaizen/**\r\n---\r\nBody\r\n";
        assertEquals("---\r\npaths: [\"projects/kaizen/**\"]\r\n---\r\nBody\r\n", narrowed(rule, List.of("projects/kaizen/**")));
    }

    @Test
    void narrow_noPatternKept_isEmpty() {
        assertEquals(Optional.empty(), RuleNarrower.narrow(bytes("---\npaths: [a/**]\n---\n"), List.of()));
    }

    @Test
    void narrow_withoutPathsKey_throws() {
        assertThrows(IllegalArgumentException.class, () -> RuleNarrower.narrow(bytes("---\nname: x\n---\n"), List.of("a")));
    }

    @Test
    void patterns_commaSeparatedString_keepsBraceGroupsWhole() {
        Frontmatter frontmatter = FrontmatterParser.parse("---\npaths: \"a/{b,c}/**, d/**\"\n---\n");
        assertEquals(List.of("a/{b,c}/**", "d/**"), RuleNarrower.patterns(frontmatter));
    }

    @Test
    void isNarrowable_readsTheThreeFormsAndRefusesNone() {
        assertTrue(RuleNarrower.isNarrowable(bytes("---\npaths: [a/**]\n---\n")));
        assertTrue(RuleNarrower.isNarrowable(bytes("---\npaths:\n- a/**\n- b/**\n---\n")));
        assertTrue(RuleNarrower.isNarrowable(bytes("---\npaths: a/**, b/**\n---\n")));
        assertFalse(RuleNarrower.isNarrowable(bytes("---\nname: x\n---\n")));
        assertFalse(RuleNarrower.isNarrowable(bytes("# no frontmatter\n")));
    }
}
