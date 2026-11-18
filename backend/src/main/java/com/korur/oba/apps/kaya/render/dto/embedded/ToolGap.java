package com.korur.oba.apps.kaya.render.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * A path an AI tool reads that Kaya does not deliver, so that a reader knows where the rest of the
 * tool's setup lives.
 *
 * @param path the path, from the repository root or the user's home
 * @param note what the tool reads there and why Kaya leaves it out
 */
public record ToolGap(
        String path,
        String note
) {
}
