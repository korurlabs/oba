package com.korur.oba.apps.kaya.settings.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kaya.settings.constant.SettingsConstant;

/**
 * Parses a .claude/settings.json into a {@link SettingsDocument}. Unknown keys are kept out of the result
 * but are not errors, because Claude Code adds settings over time and a valid file must stay valid.
 */
@Component
@RequiredArgsConstructor
public class SettingsParser {

    private final ObjectMapper objectMapper;

    /**
     * Parses settings bytes.
     *
     * @param content the file content
     * @return the settings, or empty when the content is not a JSON object
     */
    public Optional<SettingsDocument> parse(byte[] content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (IOException e) {
            // rule-10-no-op -- reason: invalid JSON is the answer, not a failure; the caller rejects the
            // file, or shows no settings and logs a warning.
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        JsonNode permissions = root.get(SettingsConstant.SETTINGS_PERMISSIONS);
        JsonNode hooks = root.get(SettingsConstant.SETTINGS_HOOKS);
        return Optional.of(new SettingsDocument(
                rules(permissions, SettingsConstant.SETTINGS_ALLOW),
                rules(permissions, SettingsConstant.SETTINGS_ASK),
                rules(permissions, SettingsConstant.SETTINGS_DENY),
                hooks(hooks),
                permissions,
                hooks));
    }

    private static List<String> rules(JsonNode permissions, String key) {
        List<String> rules = new ArrayList<>();
        if (permissions != null && permissions.get(key) != null && permissions.get(key).isArray()) {
            permissions.get(key).forEach(rule -> rules.add(rule.asText()));
        }
        return rules;
    }

    // Flattens {event: [{matcher?, hooks: [{type, command}]}]} into one row per hook command.
    private static List<SettingsHook> hooks(JsonNode hooks) {
        List<SettingsHook> rows = new ArrayList<>();
        if (hooks == null || !hooks.isObject()) {
            return rows;
        }
        for (Map.Entry<String, JsonNode> event : hooks.properties()) {
            if (!event.getValue().isArray()) {
                continue;
            }
            for (JsonNode group : event.getValue()) {
                String matcher = textOrNull(group.get(SettingsConstant.SETTINGS_MATCHER));
                JsonNode commands = group.get(SettingsConstant.SETTINGS_HOOKS);
                if (commands == null || !commands.isArray()) {
                    continue;
                }
                for (JsonNode command : commands) {
                    rows.add(new SettingsHook(event.getKey(), matcher,
                            textOrNull(command.get(SettingsConstant.SETTINGS_TYPE)),
                            textOrNull(command.get(SettingsConstant.SETTINGS_COMMAND))));
                }
            }
        }
        return rows;
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
