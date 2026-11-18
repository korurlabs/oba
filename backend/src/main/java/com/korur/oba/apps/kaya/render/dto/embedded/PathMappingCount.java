package com.korur.oba.apps.kaya.render.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * A {@link PathMapping} row with the number of files a scope's versions deliver at it.
 *
 * @param mapping the row
 * @param count   the files of the row in the versions asked for; null when no version was asked for
 */
public record PathMappingCount(
        PathMapping mapping,
        Integer count
) {
}
