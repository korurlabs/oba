package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Where a delivered symlink ends once every delivered link on its way is followed.
 *
 * @param path the repository-relative path it ends at; null when it leaves the repository or loops
 * @param loop whether it passes more links than a chain may have, which a file system reports as a loop
 */
public record SymlinkResolution(
        String path,
        boolean loop
) {

    public static SymlinkResolution of(String path) {
        return new SymlinkResolution(path, false);
    }

    public static SymlinkResolution outsideRepository() {
        return new SymlinkResolution(null, false);
    }

    public static SymlinkResolution looping() {
        return new SymlinkResolution(null, true);
    }

    /** Whether it leaves the repository; a loop is reported as one, not as this. */
    public boolean outside() {
        return path == null && !loop;
    }
}
