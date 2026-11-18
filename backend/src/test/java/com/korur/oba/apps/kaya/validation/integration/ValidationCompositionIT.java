package com.korur.oba.apps.kaya.validation.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The symlink and letter-case rules of a repository composition as the PUT of a pack list applies
 * them. The only symlink a version stores is the link of its own skill, so a link that dangles,
 * loops or leaves the repository cannot reach a composition; storage refuses it first. A refused
 * composition stores nothing: no pack rows and no release.
 */
class ValidationCompositionIT extends AbstractVersionIT {

    private static final String SKILL = "---\nname: %s\ndescription: A skill\n---\n# Skill\n";

    private Repository repository;

    @BeforeEach
    void setUp() {
        initTenant();
        repository = saveRepository(tenant.organization());
    }

    private static String skill(String name) {
        return String.format(SKILL, name);
    }

    private static SourceFile file(String path, String content) {
        return SourceFile.of(path, FileMode.FILE, content.getBytes(StandardCharsets.UTF_8));
    }

    private static SourceFile link(String path, String target) {
        return new SourceFile(path, FileMode.SYMLINK, target, null, null);
    }

    /** A pack with one approved version holding exactly the given files and links. */
    private UUID packWithLinks(String name, SourceFile... files) {
        UUID pack = insertPack(tenant.organization(), name);
        UUID version = approvedVersion(pack, 1);
        renderService.parse(tenant.organization().getId(), version, List.of(files));
        return pack;
    }

    /** The rule and path pairs a refused PUT names, as {@code RULE@path}. */
    private List<String> violations(MvcResult result) throws Exception {
        List<String> rules = list(result, "$.error.errorValues.violations[*].rule");
        List<Object> paths = JsonPath.read(result.getResponse().getContentAsString(), "$.error.errorValues.violations[*].path");
        List<String> pairs = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            pairs.add(rules.get(index) + "@" + (index < paths.size() ? paths.get(index) : ""));
        }
        return pairs;
    }

    private MvcResult refusedPut(UUID... packs) throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(packs)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        assertEquals("KAY_CFG_001", read(result, "$.error.code"));
        assertEquals(0, count("SELECT count(*) FROM kaya.repository_packs WHERE repository_id = ?", repository.getId()));
        assertEquals(0, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId()));
        return result;
    }

    @Test
    void replacePacks_skillWithItsOwnLink_isReleasedWithBothPaths() throws Exception {
        UUID pack = packWithLinks("good", file(".agents/skills/real/SKILL.md", skill("real")),
                link(".claude/skills/real", "../../.agents/skills/real"));

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(pack)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.release.number").value(1))
                .andExpect(jsonPath("$.data.release.fileCount").value(2));
    }

    @Test
    void replacePacks_skillDirectoriesThatDifferOnlyInCaseAcrossPacks_answer422() throws Exception {
        UUID first = packWithLinks("first", file(".agents/skills/Real/SKILL.md", skill("Real")),
                link(".claude/skills/Real", "../../.agents/skills/Real"));
        UUID second = packWithLinks("second", file(".agents/skills/real/SKILL.md", skill("real")),
                link(".claude/skills/real", "../../.agents/skills/real"));

        List<String> violations = violations(refusedPut(first, second));

        assertTrue(violations.stream().anyMatch(violation -> violation.startsWith("PATH_CASE_CONFLICT@.agents/skills/")),
                violations.toString());
    }

    @Test
    void parse_skillLinkLeavingItsSkill_throwsInvalidFileAndStoresNoSkill() {
        UUID pack = insertPack(tenant.organization(), "escape");
        UUID version = approvedVersion(pack, 1);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> renderService.parse(tenant.organization().getId(), version, List.of(
                        file(".agents/skills/real/SKILL.md", skill("real")),
                        link(".claude/skills/real", "../../../etc"))));

        assertEquals("KAY_CFG_027", exception.getErrorCode());
        assertEquals(0, count("SELECT count(*) FROM kaya.skills WHERE version_id = ?", version));
    }
}
