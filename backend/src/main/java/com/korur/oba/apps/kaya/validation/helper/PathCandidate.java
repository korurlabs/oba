package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * One delivered path of a repository's composition as {@link CompositionRules} reads it: only
 * what the composition rules check, so validation needs no type of the domain that composes.
 *
 * @param path          path relative to the repository root
 * @param symlink       whether the path is delivered as a symlink
 * @param symlinkTarget relative target of a symlink; null otherwise
 * @param size          content bytes; null for a symlink
 * @param alwaysOn      whether Claude Code loads it at every session start
 * @param ownerId       the pack or project that gives the path
 * @param deny          the deny rules of a guardrail, which the CLI merges into .claude/settings.json;
 *                      null for every other file and for a guardrail without deny rules
 */
public record PathCandidate(
        String path,
        boolean symlink,
        String symlinkTarget,
        Integer size,
        boolean alwaysOn,
        UUID ownerId,
        List<String> deny
) {
}
