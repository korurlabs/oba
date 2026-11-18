package com.korur.oba.apps.kaya.render.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The format a file of a {@link com.korur.oba.apps.kaya.render.dto.embedded.PathMapping} row holds,
 * so that a reader knows what to write at that path.
 *
 * - MARKDOWN: Markdown, optionally opened by a YAML frontmatter block that holds the index fields.
 * - JSON: a JSON document.
 * - YAML: a YAML document.
 * - ANY: any bytes, such as a shell script, an image or a text file; only the path decides the kind.
 * - SYMLINK: a symbolic link, which holds no content of its own.
 */
public enum FileFormat {
    MARKDOWN,
    JSON,
    YAML,
    ANY,
    SYMLINK
}
