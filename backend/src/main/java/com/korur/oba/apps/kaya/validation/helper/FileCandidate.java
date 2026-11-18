package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * A file to check before it is stored: a saved draft file, an uploaded proposal file, a parsed file
 * or a file of a version being submitted or approved. The caller reads the frontmatter, so the
 * validation domain depends on no file format or file kind.
 *
 * @param path          the path relative to the repository root
 * @param symlink       whether the file is a symlink rather than a file with content
 * @param symlinkTarget the relative target of a symlink; null otherwise
 * @param content       the bytes of a file; null for a symlink
 * @param frontmatter   the {@link FrontmatterFacts} of a SKILL.md; {@link FrontmatterFacts#NONE} for
 *                      every other file
 */
public record FileCandidate(
        String path,
        boolean symlink,
        String symlinkTarget,
        byte[] content,
        FrontmatterFacts frontmatter
) {
}
