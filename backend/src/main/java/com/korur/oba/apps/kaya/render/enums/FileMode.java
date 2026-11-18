package com.korur.oba.apps.kaya.render.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * How a file of a version is written into a checkout. A SYMLINK has a target and no content; FILE
 * and EXECUTABLE have content, and only EXECUTABLE gets the executable bit.
 */
public enum FileMode {
    FILE,
    EXECUTABLE,
    SYMLINK
}
