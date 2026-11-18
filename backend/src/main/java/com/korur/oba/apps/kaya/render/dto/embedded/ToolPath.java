package com.korur.oba.apps.kaya.render.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.AgentTool;
import com.korur.oba.apps.kaya.render.enums.ToolLoading;

/**
 * One path an AI tool reads, with the {@link PathMapping} rows that bring it there, as
 * {@link com.korur.oba.apps.kaya.render.helper.ToolPaths} lists it.
 *
 * @param tool     the tool that reads the path
 * @param readPath the path the tool opens, from the repository root, with placeholders in angle brackets
 * @param loading  when the tool loads it
 * @param link     the link row the tool passes on the way to the source; null when it reads the source directly
 * @param direct   the row of a regular file Kaya can deliver at the read path instead of the link,
 *                 such as the root AGENTS.md; null when there is none
 * @param source   the row of the files the path ends at
 * @param note     what else decides the read, such as a setting the tool needs; null when nothing
 */
public record ToolPath(
        AgentTool tool,
        String readPath,
        ToolLoading loading,
        PathMapping link,
        PathMapping direct,
        PathMapping source,
        String note
) {
}
