package com.korur.oba.apps.kaya.render.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;

/**
 * One file of a version's tree as {@link com.korur.oba.apps.kaya.render.service.RenderQueryService}
 * writes it: the stored bytes unchanged, at the delivery path of its kind.
 *
 * @param path          the path relative to the repository root
 * @param mode          {@link FileMode#FILE}, {@link FileMode#EXECUTABLE}, or {@link FileMode#SYMLINK}
 *                      for the link of a skill
 * @param symlinkTarget the target of a symlink; null otherwise
 * @param content       the stored bytes; null for a symlink
 * @param sha256        the lower-case hex SHA-256 of the content; null for a symlink
 * @param contentBlobId the stored blob of the content; null for a symlink
 * @param overridesPath the inherited path a rule or convention replaces; null when it replaces none
 * @param entityKind    the {@link EntityKind} of the row the file renders from, a skill part by its
 *                      own kind; null for a symlink, which no row stores
 * @param entityId      the id of that row, so that an editor can open the entity behind a path;
 *                      null for a symlink
 */
public record RenderedFile(
        String path,
        FileMode mode,
        String symlinkTarget,
        byte[] content,
        String sha256,
        UUID contentBlobId,
        String overridesPath,
        EntityKind entityKind,
        UUID entityId
) {

    /**
     * A file that no row stores, such as the link of a skill, with no entity kind and no entity id.
     *
     * @param path          the path relative to the repository root
     * @param mode          the mode
     * @param symlinkTarget the target of a symlink; null otherwise
     * @param content       the bytes; null for a symlink
     * @param sha256        the lower-case hex SHA-256 of the content; null for a symlink
     * @param contentBlobId the stored blob of the content; null for a symlink
     * @param overridesPath the inherited path the file replaces; null when it replaces none
     */
    public RenderedFile(String path, FileMode mode, String symlinkTarget, byte[] content, String sha256, UUID contentBlobId,
                        String overridesPath) {
        this(path, mode, symlinkTarget, content, sha256, contentBlobId, overridesPath, null, null);
    }

    /**
     * The file as a source to parse again, so a rendered version can be copied into another.
     *
     * @return the source file with the same path, mode, target, bytes and override
     */
    public SourceFile toSource() {
        return new SourceFile(path, mode, symlinkTarget, content, overridesPath);
    }
}
