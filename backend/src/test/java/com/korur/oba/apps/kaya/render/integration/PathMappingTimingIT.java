package com.korur.oba.apps.kaya.render.integration;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.mock_data.RenderTrees;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /path-mappings for the most versions a request may name, each a pack of about 600 files with 46
 * skills, the size of a real agent configuration. The counts must not cost a query per skill or per
 * link. The guard counts the SQL statements of one call, which does not depend on the speed of the
 * machine; the wall time is only logged.
 */
class PathMappingTimingIT extends AbstractVersionIT {

    private static final Logger LOG = LoggerFactory.getLogger(PathMappingTimingIT.class);

    private static final int VERSIONS = 20;
    private static final int SKILLS = 46;
    private static final int RUNS = 5;
    // One call costs a fixed handful of statements plus thirteen reads per version (its agents files and
    // the twelve counted kinds). The slack covers the statements that resolve the tenant and the versions.
    private static final int FIXED_STATEMENTS = 3;
    private static final int STATEMENTS_PER_VERSION = 13;
    private static final int STATEMENT_SLACK = 10;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private boolean statisticsWereEnabled;

    private final List<UUID> versionIds = new ArrayList<>();
    private int filesPerVersion;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statisticsWereEnabled = statistics.isStatisticsEnabled();
        initTenant();
        List<SourceFile> tree = realisticTree();
        filesPerVersion = tree.size();
        for (int index = 0; index < VERSIONS; index++) {
            UUID versionId = approvedVersion(insertPack(tenant.organization(), "timing-" + index), 1);
            renderService.parse(tenant.organization().getId(), versionId, tree);
            versionIds.add(versionId);
        }
        // Statistics are on for this test only, and only after the setup, so its statements are not counted.
        statistics.setStatisticsEnabled(true);
    }

    @AfterEach
    void restoreStatistics() {
        statistics.setStatisticsEnabled(statisticsWereEnabled);
    }

    @Test
    void getPathMappings_twentyRealisticVersions_costsAFixedNumberOfStatementsPerVersion() throws Exception {
        // Arrange
        StringBuilder query = new StringBuilder("/kaya/path-mappings?versionId=" + versionIds.getFirst());
        versionIds.stream().skip(1).forEach(versionId -> query.append("&versionId=").append(versionId));
        // The first call warms the plan caches and the connection pool, as a running server has.
        perform(query.toString());

        // Act
        statistics.clear();
        perform(query.toString());
        long statements = statistics.getPrepareStatementCount();
        long[] millis = new long[RUNS];
        MvcResult result = null;
        for (int run = 0; run < RUNS; run++) {
            long start = System.nanoTime();
            result = perform(query.toString());
            millis[run] = (System.nanoTime() - start) / 1_000_000;
        }

        // Assert
        Arrays.sort(millis);
        long median = millis[RUNS / 2];
        LOG.info("GET /path-mappings for {} versions of {} files: median {} ms, runs {} ms", VERSIONS, filesPerVersion, median,
                Arrays.toString(millis));
        List<Integer> counts = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*].count");
        assertEquals(VERSIONS * filesPerVersion, counts.stream().mapToInt(Integer::intValue).sum());
        LOG.info("GET /path-mappings for {} versions: {} SQL statements", VERSIONS, statements);
        assertTrue(statements > 0, "no statements counted, so the statistics are not recording");
        long limit = FIXED_STATEMENTS + (long) STATEMENTS_PER_VERSION * VERSIONS + STATEMENT_SLACK;
        assertTrue(statements <= limit, statements + " statements for " + VERSIONS + " versions of " + SKILLS
                + " skills, at most " + limit + " expected");
    }

    private MvcResult perform(String query) throws Exception {
        return mockMvc.perform(in(tenant, MockMvcRequestBuilders.get(query), member, null))
                .andExpect(status().isOk())
                .andReturn();
    }

    // The base tree with enough rules, conventions, agents and skills to reach the size of a
    // real configuration; every file has its own content, as in a real pack.
    private static List<SourceFile> realisticTree() {
        List<SourceFile> tree = new ArrayList<>(RenderTrees.base());
        for (int index = 0; index < 30; index++) {
            tree.add(file(".agents/rules/rule-" + index + ".md", "# Rule " + index + "\n"));
            tree.add(file("docs/private/conventions/area/convention-" + index + ".md", "# Convention " + index + "\n"));
        }
        for (int index = 0; index < 10; index++) {
            tree.add(file(".agents/roles/role-" + index + ".md", "# Role " + index + "\n"));
            tree.add(file(".agents/workflows/workflow-" + index + ".md", "# Workflow " + index + "\n"));
            tree.add(file(".agents/templates/TEMPLATE_" + index + ".md", "# Template " + index + "\n"));
            tree.add(file(".claude/agents/agent-" + index + ".md", "---\nname: agent-" + index + "\ndescription: Agent " + index
                    + "\n---\nWork.\n"));
        }
        for (int index = 0; index < SKILLS; index++) {
            String name = "skill-" + index;
            String directory = ".agents/skills/" + name + "/";
            tree.add(file(directory + "SKILL.md", "---\nname: " + name + "\ndescription: Skill " + index + "\n---\n\n# " + name + "\n"));
            tree.add(file(directory + "workflows/start.md", "# Start " + index + "\n"));
            tree.add(file(directory + "workflows/finish.md", "# Finish " + index + "\n"));
            tree.add(SourceFile.of(directory + "scripts/run.sh", FileMode.EXECUTABLE, utf8("#!/bin/sh\necho " + index + "\n")));
            tree.add(SourceFile.of(directory + "scripts/check.sh", FileMode.EXECUTABLE, utf8("#!/bin/sh\necho check " + index + "\n")));
            tree.add(file(directory + "conventions/style.md", "# Style " + index + "\n"));
            tree.add(file(directory + "rules/naming.md", "# Naming " + index + "\n"));
            tree.add(file(directory + "references/api.md", "# API " + index + "\n"));
            tree.add(file(directory + "references/examples.md", "# Examples " + index + "\n"));
            tree.add(file(directory + "references/glossary.md", "# Glossary " + index + "\n"));
            tree.add(file(directory + "agents/openai.yml", "interface:\n  display_name: Skill " + index + "\n"));
            tree.add(new SourceFile(".claude/skills/" + name, FileMode.SYMLINK, "../../.agents/skills/" + name, null, null));
        }
        return tree;
    }

    private static SourceFile file(String path, String content) {
        return SourceFile.of(path, FileMode.FILE, utf8(content));
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
