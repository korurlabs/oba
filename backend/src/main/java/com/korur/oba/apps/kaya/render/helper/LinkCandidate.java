package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * A link one delivered Markdown file makes to a path Kaya may deliver, before it is checked against
 * what the scope delivers.
 *
 * @param sourcePath the delivered file that holds the link
 * @param targetPath the repository-relative target; a directory ends with a slash
 * @param line       the 1-based line of the link in the source
 */
public record LinkCandidate(
        String sourcePath,
        String targetPath,
        int line
) {
}
