package com.korur.oba.apps.kaya.settings.helper;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.databind.JsonNode;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * The parts of a .claude/settings.json that Kaya reads: the permission rules and the
 * {@link SettingsHook} registrations.
 *
 * @param allow       permissions.allow rules
 * @param ask         permissions.ask rules
 * @param deny        permissions.deny rules
 * @param hooks       each registered hook command, by event
 * @param permissions the permissions object as written; null when absent
 * @param hooksNode   the hooks object as written; null when absent
 */
public record SettingsDocument(
        List<String> allow,
        List<String> ask,
        List<String> deny,
        List<SettingsHook> hooks,
        JsonNode permissions,
        JsonNode hooksNode
) {

    /**
     * A settings file without rules or hooks, shown when a version has no settings file or its file is
     * not a JSON object.
     */
    public static final SettingsDocument EMPTY = new SettingsDocument(List.of(), List.of(), List.of(), List.of(), null, null);

    /**
     * Whether a hook of the event runs a command that contains the text.
     *
     * @param event       the hook event, such as PreToolUse
     * @param commandPart the text the command must contain
     * @return true when such a hook is registered
     */
    public boolean registers(String event, String commandPart) {
        return hooks.stream()
                .anyMatch(hook -> hook.event().equals(event) && hook.command() != null && hook.command().contains(commandPart));
    }
}
