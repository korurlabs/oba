package com.korur.oba.apps.kaya.render.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileMode;

/**
 * One file handed to {@link com.korur.oba.apps.kaya.render.service.RenderService} to store, at its
 * delivered path, or the removal of one path.
 *
 * @param path          the path relative to the repository root, such as .agents/rules/00-core.md
 * @param mode          {@link FileMode#FILE} or {@link FileMode#EXECUTABLE} for content,
 *                      {@link FileMode#SYMLINK} for the link of a skill; null for a removal
 * @param symlinkTarget the target of a symlink; null otherwise
 * @param content       the exact bytes of the file; null for a symlink or a removal
 * @param overridesPath the inherited path a rule or convention replaces; null when it replaces none
 */
public record SourceFile(
        String path,
        FileMode mode,
        String symlinkTarget,
        byte[] content,
        String overridesPath
) {

    /**
     * A file with content.
     *
     * @param path    the path
     * @param mode    {@link FileMode#FILE} or {@link FileMode#EXECUTABLE}
     * @param content the bytes
     * @return the source file, overriding nothing
     */
    public static SourceFile of(String path, FileMode mode, byte[] content) {
        return new SourceFile(path, mode, null, content, null);
    }

    /**
     * The removal of one path, for a single-file delete.
     *
     * @param path the path to remove
     * @return the removal
     */
    public static SourceFile removal(String path) {
        return new SourceFile(path, null, null, null, null);
    }

    /** Whether this removes the path instead of storing a file. */
    public boolean isRemoval() {
        return mode == null;
    }

    /** Whether the file is a symlink. */
    public boolean isSymlink() {
        return mode == FileMode.SYMLINK;
    }
}
