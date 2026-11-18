package com.korur.oba.apps.kaya.render.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.ToolPathState;

/**
 * A {@link ToolPath} with what a scope's versions deliver of it.
 *
 * @param path  the path the tool reads
 * @param state whether the scope gives the tool that path; null when no version was asked for
 * @param count the files the tool finds there: the regular file's count when one is delivered at the
 *              read path, the source row's count otherwise; null when no version was asked for
 */
public record ToolPathCount(
        ToolPath path,
        ToolPathState state,
        Integer count
) {
}
