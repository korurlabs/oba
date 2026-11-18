package com.korur.oba.apps.kaya.agentsfile.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Where an agents file is delivered; a version holds at most one agents file per placement.
 */
public enum AgentsFilePlacement {
    // AGENTS.md at the repository root.
    ROOT,
    // AGENTS.md inside .agents/.
    AGENTS_DIR,
    // AGENTS.md at the folder a pack is mounted in, below the repository root.
    MOUNT_ROOT
}
