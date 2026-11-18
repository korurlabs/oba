package com.korur.oba.apps.kaya.release.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.validation.helper.PathCandidate;

/**
 * One path of a repository's {@link Composition}, with the pack and the version that
 * give it.
 *
 * @param path          path relative to the repository root
 * @param mode          FILE, EXECUTABLE or SYMLINK
 * @param sha256        hash of the content; null for a SYMLINK
 * @param size          content bytes; null for a SYMLINK
 * @param symlinkTarget relative target of a SYMLINK; null otherwise
 * @param alwaysOn      whether Claude Code loads it at every session start
 * @param ownerId       the pack that gives the path
 * @param versionId     the version of that owner
 * @param overridesPath the inherited path a mounted pack's file overrides; null for every other file
 * @param narrowedBy    the mounted pack whose override narrowed this inherited rule's paths, so its
 *                      sha256 and size are those of the narrowed copy; null when not narrowed
 * @param deny          the deny rules a guardrail's frontmatter names; null for every other file
 *                      and for a guardrail without deny rules
 */
public record ComposedFile(
        String path,
        FileMode mode,
        String sha256,
        Integer size,
        String symlinkTarget,
        boolean alwaysOn,
        UUID ownerId,
        UUID versionId,
        String overridesPath,
        UUID narrowedBy,
        List<String> deny
) {

    /**
     * Whether the path is delivered as a symlink.
     *
     * @return true for {@link FileMode#SYMLINK}
     */
    public boolean symlink() {
        return mode == FileMode.SYMLINK;
    }

    /**
     * The path as the composition rules of validation read it.
     *
     * @return the {@link PathCandidate} of this path
     */
    public PathCandidate toCandidate() {
        return new PathCandidate(path, symlink(), symlinkTarget, size, alwaysOn, ownerId, deny);
    }
}
