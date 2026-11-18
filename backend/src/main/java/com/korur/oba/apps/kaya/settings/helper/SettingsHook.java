package com.korur.oba.apps.kaya.settings.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * One hook registration of a settings file.
 *
 * @param event   the hook event, such as PreToolUse
 * @param matcher the tool matcher of its group; null when the group has none
 * @param type    the hook type, such as command
 * @param command the command it runs; null for a hook type without one
 */
public record SettingsHook(String event, String matcher, String type, String command) {
}
