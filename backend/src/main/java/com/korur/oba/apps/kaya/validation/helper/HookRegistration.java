package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * One hook command a delivered settings file registers, as the caller read it, so
 * {@link CompositionRules} can check the hook dispatcher without parsing the settings itself.
 *
 * @param event   the hook event, such as PreToolUse
 * @param command the command it runs; null for a hook type without one
 */
public record HookRegistration(String event, String command) {
}
