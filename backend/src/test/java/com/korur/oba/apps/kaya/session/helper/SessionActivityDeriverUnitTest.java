package com.korur.oba.apps.kaya.session.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// 2. Third-party

// 3. Java SDK
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// 4. Internal
import com.korur.oba.apps.kaya.session.dto.derived.DerivedSkillUse;
import com.korur.oba.apps.kaya.session.dto.derived.DerivedSubagent;
import com.korur.oba.apps.kaya.session.dto.derived.SessionActivity;
import com.korur.oba.apps.kaya.session.enums.SessionSkillUseSource;
import com.korur.oba.apps.kaya.support.RunFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionActivityDeriverUnitTest {

    private final SessionActivityDeriver deriver = new SessionActivityDeriver();

    private int clock;

    static Stream<String> fixtureRuns() {
        return RunFixtures.RUN_NAMES.stream();
    }

    private Map<String, Object> event(String name, Map<String, Object> fields) {
        clock += 1;
        Map<String, Object> event = new HashMap<>(fields);
        event.put("event", name);
        event.put("at", String.format("2026-10-23T10:00:%02dZ", clock % 60));
        return event;
    }

    @Test
    void fixtures_coverEveryRunDirectoryOfTheFrontend() throws Exception {
        List<String> directories;
        try (Stream<Path> entries = Files.list(Path.of("..", "frontend", "apps", "kaya", "fixtures", "runs"))) {
            directories = entries.map(path -> path.getFileName().toString()).sorted().toList();
        }

        assertEquals(directories, RunFixtures.RUN_NAMES.stream().sorted().toList());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureRuns")
    void derive_fixtureRun_givesEachSubagentOnceWithAStart(String run) {
        SessionActivity result = deriver.derive(RunFixtures.events(run));

        List<String> ids = result.subagents().stream().map(DerivedSubagent::agentId).toList();
        assertEquals(ids.size(), ids.stream().distinct().count(), ids.toString());
        result.subagents().forEach(subagent -> assertNotNull(subagent.startedAt()));
    }

    @Test
    void derive_planWaiting_listsTheRequirementsAnalyst() {
        SessionActivity result = deriver.derive(RunFixtures.events("plan-waiting"));

        List<String> types = result.subagents().stream().map(DerivedSubagent::subagentType).toList();
        assertTrue(types.contains("requirements-analyst"), types.toString());
    }

    @Test
    void derive_implementRunning_hasOneSubagentStillRunning() {
        SessionActivity result = deriver.derive(RunFixtures.events("implement-running"));

        long running = result.subagents().stream().filter(subagent -> subagent.endedAt() == null).count();
        assertEquals(1L, running);
    }

    @Test
    void derive_plain_hasNoSubagentAndNoSkillUse() {
        SessionActivity result = deriver.derive(RunFixtures.events("plain"));

        assertTrue(result.subagents().isEmpty());
        assertTrue(result.skillUses().isEmpty());
    }

    @Test
    void derive_agentCallThenSpawnAndStop_givesTheSubagentWithItsModelAndEnd() {
        List<Map<String, Object>> events = List.of(
                event("PreToolUse", Map.of("tool_name", "Agent", "tool_use_id", "call-1",
                        "subagent_type", "coding-engineer", "description", "Build it", "model", "opus")),
                event("SubagentStart", Map.of("agent_id", "agent-1", "agent_type", "coding-engineer")),
                event("PostToolUse", Map.of("tool_name", "Agent", "tool_use_id", "call-1",
                        "spawned_agent_id", "agent-1")),
                event("SubagentStop", Map.of("agent_id", "agent-1")));

        SessionActivity result = deriver.derive(events);

        assertEquals(1, result.subagents().size());
        DerivedSubagent subagent = result.subagents().get(0);
        assertEquals("agent-1", subagent.agentId());
        assertEquals("coding-engineer", subagent.subagentType());
        assertEquals("opus", subagent.model());
        assertEquals("Build it", subagent.description());
        assertNotNull(subagent.endedAt());
    }

    @Test
    void derive_failedAgentCall_givesNoSubagent() {
        List<Map<String, Object>> events = List.of(
                event("PreToolUse", Map.of("tool_name", "Task", "tool_use_id", "call-1",
                        "subagent_type", "coding-engineer")),
                event("PostToolUseFailure", Map.of("tool_name", "Task", "tool_use_id", "call-1")));

        SessionActivity result = deriver.derive(events);

        assertTrue(result.subagents().isEmpty());
    }

    @Test
    void derive_commandAndSkillTool_countsEachUsePerSource() {
        List<Map<String, Object>> events = List.of(
                event("UserPromptExpansion", Map.of("command_name", "plan")),
                event("PreToolUse", Map.of("tool_name", "Skill", "skill", "logging-convention")),
                event("PreToolUse", Map.of("tool_name", "Skill", "skill", "logging-convention")),
                event("PostToolUse", Map.of("tool_name", "Skill", "skill", "logging-convention")),
                event("UnknownEvent", Map.of("command_name", "ignored")));

        SessionActivity result = deriver.derive(events);

        assertEquals(2, result.skillUses().size());
        DerivedSkillUse command = result.skillUses().get(0);
        assertEquals("plan", command.skill());
        assertEquals(SessionSkillUseSource.COMMAND, command.source());
        assertEquals(1, command.useCount());
        DerivedSkillUse tool = result.skillUses().get(1);
        assertEquals("logging-convention", tool.skill());
        assertEquals(SessionSkillUseSource.TOOL, tool.source());
        assertEquals(2, tool.useCount());
    }

    @Test
    void derive_subagentStartWithoutCall_isStillListed() {
        List<Map<String, Object>> events = List.of(
                event("SubagentStart", Map.of("agent_id", "agent-9", "agent_type", "Explore")));

        SessionActivity result = deriver.derive(events);

        assertEquals(1, result.subagents().size());
        assertEquals("Explore", result.subagents().get(0).subagentType());
        assertFalse(result.subagents().get(0).agentId().isEmpty());
    }
}
