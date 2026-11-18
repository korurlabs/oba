package com.korur.oba.apps.kaya.render.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Whether a scope's versions give an AI tool a path it reads, from the counts of the path mapping
 * rows behind it.
 *
 * - DIRECT: a regular file sits at the path the tool reads, or the tool reads the source without a
 *   link and the source is delivered.
 * - LINKED: the link the tool passes and the source it ends at are both delivered.
 * - LINK_MISSING: the source is delivered but the link is not, so the tool does not see those files.
 * - NOT_DELIVERED: the scope delivers no source for the path.
 */
public enum ToolPathState {
    DIRECT,
    LINKED,
    LINK_MISSING,
    NOT_DELIVERED
}
