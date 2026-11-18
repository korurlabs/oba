package com.korur.oba.apps.kaya.session.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 4. Internal
import com.korur.oba.apps.kaya.session.constant.SessionActivityConstant;
import com.korur.oba.apps.kaya.session.dto.derived.DerivedSkillUse;
import com.korur.oba.apps.kaya.session.dto.derived.DerivedSubagent;
import com.korur.oba.apps.kaya.session.dto.derived.SessionActivity;
import com.korur.oba.apps.kaya.session.enums.SessionSkillUseSource;

/**
 * Derives the subagents and the skill uses of a session from its recorded hook events, by the
 * same rules as the frontend, so the stored rows and the session page agree.
 *
 * The events are replayed in seq order. An Agent or Task call starts a subagent; its Post event
 * names the id Claude Code gave it, and a SubagentStart without a matching call still counts as a
 * subagent. A slash command and a Skill tool call each count as a skill use.
 */
@Component
public class SessionActivityDeriver {

    /**
     * Derives the activity of one session.
     *
     * @param events the session's events in seq order, each a map of the recorder's keys with
     *               "at", "event" and "agent_id" included; events with a name the recorder does not
     *               register are skipped, as the frontend skips them
     * @return the subagents and the skill use totals
     */
    public SessionActivity derive(List<Map<String, Object>> events) {
        List<Map<String, Object>> known = new ArrayList<>();
        for (Map<String, Object> event : events) {
            if (SessionActivityConstant.HOOK_EVENTS.contains(text(event, SessionActivityConstant.KEY_EVENT))) {
                known.add(event);
            }
        }
        Derivation derivation = new Derivation(known);
        return derivation.run();
    }

    /**
     * A string value of an event; an absent, empty or non-string value reads as absent, as a
     * falsy value does in the frontend.
     */
    private static String text(Map<String, Object> event, String key) {
        Object value = event.get(key);
        if (value instanceof String string && !string.isEmpty()) {
            return string;
        }
        return null;
    }

    private static boolean isPost(Map<String, Object> event) {
        String name = text(event, SessionActivityConstant.KEY_EVENT);
        return SessionActivityConstant.EVENT_POST_TOOL_USE.equals(name)
                || SessionActivityConstant.EVENT_POST_TOOL_USE_FAILURE.equals(name);
    }

    private static boolean okOf(Map<String, Object> event) {
        if (SessionActivityConstant.EVENT_POST_TOOL_USE_FAILURE.equals(text(event, SessionActivityConstant.KEY_EVENT))) {
            return false;
        }
        return !Boolean.FALSE.equals(event.get(SessionActivityConstant.KEY_OK));
    }

    private static final class AgentRun {
        private String toolUseId;
        private String agentId;
        private String subagentType;
        private String description;
        private String startedAt;
        private String stoppedAt;
        private boolean active = true;
        private String callModel;
    }

    private record SkillUse(String name, SessionSkillUseSource source, String at) {
    }

    private static final class Derivation {

        private final List<Map<String, Object>> events;
        private final Map<String, String> spawned = new HashMap<>();
        private final Set<String> spawningCalls = new LinkedHashSet<>();
        private final List<AgentRun> agents = new ArrayList<>();
        private final List<SkillUse> skills = new ArrayList<>();
        // The first model an event of each subagent named.
        private final Map<String, String> agentModels = new HashMap<>();
        private boolean ended;

        private Derivation(List<Map<String, Object>> events) {
            this.events = events;
        }

        private SessionActivity run() {
            // The Post event of an Agent call names the subagent it started; reading these first
            // lets a SubagentStart find its call even when the Post comes later, as it does for a
            // subagent that runs in the foreground.
            for (Map<String, Object> event : events) {
                String tool = text(event, SessionActivityConstant.KEY_TOOL_NAME);
                String spawnedId = text(event, SessionActivityConstant.KEY_SPAWNED_AGENT_ID);
                String toolUseId = text(event, SessionActivityConstant.KEY_TOOL_USE_ID);
                if (isPost(event) && tool != null && SessionActivityConstant.AGENT_TOOLS.contains(tool)
                        && spawnedId != null && toolUseId != null) {
                    spawned.put(spawnedId, toolUseId);
                }
            }
            spawningCalls.addAll(spawned.values());

            for (Map<String, Object> event : events) {
                String agentId = text(event, SessionActivityConstant.KEY_AGENT_ID);
                String model = text(event, SessionActivityConstant.KEY_MODEL);
                if (agentId != null && model != null) {
                    agentModels.putIfAbsent(agentId, model);
                }
                replay(event);
            }

            if (ended) {
                for (AgentRun agent : agents) {
                    agent.active = false;
                }
            }
            return new SessionActivity(subagents(), skillUses());
        }

        private AgentRun agentById(String agentId) {
            for (AgentRun agent : agents) {
                if (agentId != null && agentId.equals(agent.agentId)) {
                    return agent;
                }
            }
            return null;
        }

        private AgentRun newAgent(Map<String, Object> event, String subagentType, String toolUseId) {
            AgentRun agent = new AgentRun();
            agent.toolUseId = toolUseId;
            agent.subagentType = subagentType;
            agent.description = text(event, SessionActivityConstant.KEY_DESCRIPTION);
            agent.startedAt = text(event, SessionActivityConstant.KEY_AT);
            agent.callModel = text(event, SessionActivityConstant.KEY_MODEL);
            agents.add(agent);
            return agent;
        }

        private void useSkill(String name, SessionSkillUseSource source, Map<String, Object> event) {
            skills.add(new SkillUse(name, source, text(event, SessionActivityConstant.KEY_AT)));
        }

        private void replay(Map<String, Object> event) {
            String name = text(event, SessionActivityConstant.KEY_EVENT);
            String agentId = text(event, SessionActivityConstant.KEY_AGENT_ID);
            switch (name) {
                case SessionActivityConstant.EVENT_USER_PROMPT_EXPANSION -> {
                    String command = text(event, SessionActivityConstant.KEY_COMMAND_NAME);
                    if (command != null) {
                        useSkill(command, SessionSkillUseSource.COMMAND, event);
                    }
                    return;
                }
                case SessionActivityConstant.EVENT_SESSION_END -> {
                    ended = true;
                    return;
                }
                case SessionActivityConstant.EVENT_SUBAGENT_START -> {
                    subagentStart(event, agentId);
                    return;
                }
                case SessionActivityConstant.EVENT_SUBAGENT_STOP -> {
                    subagentStop(event, agentId);
                    return;
                }
                default -> {
                    // Tool events are handled below; the other events carry no activity.
                }
            }

            String tool = text(event, SessionActivityConstant.KEY_TOOL_NAME);
            String toolUseId = text(event, SessionActivityConstant.KEY_TOOL_USE_ID);
            if (tool != null && SessionActivityConstant.AGENT_TOOLS.contains(tool)) {
                agentCall(event, name, toolUseId);
                return;
            }
            if (SessionActivityConstant.TOOL_SKILL.equals(tool)) {
                String skill = text(event, SessionActivityConstant.KEY_SKILL);
                if (SessionActivityConstant.EVENT_PRE_TOOL_USE.equals(name) && skill != null) {
                    useSkill(skill, SessionSkillUseSource.TOOL, event);
                }
            }
        }

        private void subagentStart(Map<String, Object> event, String agentId) {
            if (agentId == null) {
                return;
            }
            String callId = spawned.get(agentId);
            String agentType = text(event, SessionActivityConstant.KEY_AGENT_TYPE);
            AgentRun found = null;
            if (callId != null) {
                for (AgentRun candidate : agents) {
                    if (candidate.agentId == null && callId.equals(candidate.toolUseId)) {
                        found = candidate;
                        break;
                    }
                }
            }
            if (found == null) {
                for (AgentRun candidate : agents) {
                    boolean unspawned = candidate.toolUseId == null || !spawningCalls.contains(candidate.toolUseId);
                    if (candidate.agentId == null && candidate.active && candidate.subagentType.equals(agentType)
                            && unspawned) {
                        found = candidate;
                        break;
                    }
                }
            }
            if (found == null) {
                found = newAgent(event, agentType == null ? SessionActivityConstant.UNKNOWN_SUBAGENT_TYPE : agentType,
                        null);
            }
            found.agentId = agentId;
        }

        private void subagentStop(Map<String, Object> event, String agentId) {
            AgentRun agent = agentId == null ? null : agentById(agentId);
            if (agent == null) {
                return;
            }
            agent.active = false;
            agent.stoppedAt = text(event, SessionActivityConstant.KEY_AT);
        }

        private void agentCall(Map<String, Object> event, String name, String toolUseId) {
            if (SessionActivityConstant.EVENT_PRE_TOOL_USE.equals(name)) {
                String subagentType = text(event, SessionActivityConstant.KEY_SUBAGENT_TYPE);
                newAgent(event, subagentType == null ? SessionActivityConstant.UNKNOWN_SUBAGENT_TYPE : subagentType,
                        toolUseId);
                return;
            }
            if (!isPost(event)) {
                return;
            }
            AgentRun found = null;
            for (AgentRun candidate : agents) {
                if (candidate.toolUseId != null && candidate.toolUseId.equals(toolUseId)) {
                    found = candidate;
                    break;
                }
            }
            if (found == null || found.agentId != null) {
                return;
            }
            String spawnedId = text(event, SessionActivityConstant.KEY_SPAWNED_AGENT_ID);
            if (spawnedId != null) {
                found.agentId = spawnedId;
            } else if (!okOf(event)) {
                // The call failed before a subagent existed.
                found.active = false;
                found.stoppedAt = text(event, SessionActivityConstant.KEY_AT);
            }
        }

        private List<DerivedSubagent> subagents() {
            List<DerivedSubagent> result = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (AgentRun agent : agents) {
                if (agent.agentId == null || !seen.add(agent.agentId)) {
                    continue;
                }
                String model = agentModels.getOrDefault(agent.agentId, agent.callModel);
                result.add(new DerivedSubagent(agent.agentId, agent.subagentType, model, agent.description,
                        parse(agent.startedAt), parse(agent.stoppedAt)));
            }
            return result;
        }

        private List<DerivedSkillUse> skillUses() {
            Map<String, SkillUse> first = new LinkedHashMap<>();
            Map<String, Integer> counts = new HashMap<>();
            for (SkillUse use : skills) {
                String key = use.source().name() + SessionActivityConstant.SKILL_KEY_SEPARATOR + use.name();
                first.putIfAbsent(key, use);
                counts.merge(key, 1, Integer::sum);
            }
            List<DerivedSkillUse> result = new ArrayList<>();
            for (Map.Entry<String, SkillUse> entry : first.entrySet()) {
                SkillUse use = entry.getValue();
                result.add(new DerivedSkillUse(use.name(), use.source(), counts.get(entry.getKey()), parse(use.at())));
            }
            return result;
        }

        private static Instant parse(String at) {
            return at == null ? null : Instant.parse(at);
        }
    }
}
