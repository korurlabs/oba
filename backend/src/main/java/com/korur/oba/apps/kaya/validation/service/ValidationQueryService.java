package com.korur.oba.apps.kaya.validation.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.helper.CompositionCandidate;
import com.korur.oba.apps.kaya.validation.helper.FileCandidate;

/**
 * The rules of delivered files: per file (paths, modes, symlinks, size, skills, settings) through
 * {@link com.korur.oba.apps.kaya.validation.helper.FileRules}, and per repository composition (one
 * owner per path, letter case, symlinks, the hook dispatcher, project
 * namespaces) through {@link com.korur.oba.apps.kaya.validation.helper.CompositionRules}. It reads no
 * table and calls no other domain; callers hand it the files and compositions they hold.
 *
 * Called by {@link com.korur.oba.apps.kaya.render.service.RenderService} for the files it parses and
 * by {@link com.korur.oba.apps.kaya.release.service.ReleaseQueryService} and
 * {@link com.korur.oba.apps.kaya.release.service.ReleaseService} for the compositions they build.
 */
public interface ValidationQueryService {

    /**
     * Checks the files of one version: each file by its own rules, and the links over the whole list.
     *
     * @param files the files with their content
     * @return the violations of every file; empty when all keep the rules
     */
    List<Violation> validateFiles(List<FileCandidate> files);

    /**
     * Checks one file by the rules that read only the file itself: path, mode, link target and content.
     *
     * @param file the file with its content
     * @return its violations, each naming its path; empty when it keeps the rules
     */
    List<Violation> validateFile(FileCandidate file);

    /**
     * Checks one changed file as validateFile does, except that a size limit the file at the same
     * path already broke before the change is dropped while the file measures no more than it did:
     * a seeded skill over the line limit may be edited but gain no line, and a file over the byte
     * limit may not gain a byte. A limit the earlier file kept, or a file without an earlier one,
     * still refuses.
     *
     * @param file   the file with its content after the change
     * @param before the file at the same path before the change, with its content; null for a new file
     * @return its violations, each naming its path; empty when it keeps the rules
     */
    List<Violation> validateFileChange(FileCandidate file, FileCandidate before);

    /**
     * Checks the rules that depend on the other files of the version: letter-case conflicts, paths
     * below a link and links followed through other links. They read paths and targets only.
     *
     * @param files the files of the version
     * @return their violations, by path; empty when the links keep the rules
     */
    List<Violation> validateLinks(List<FileCandidate> files);

    /**
     * Checks one repository's composition.
     *
     * @param composition the {@link CompositionCandidate}
     * @return its violations, each naming the repository
     */
    List<Violation> validateComposition(CompositionCandidate composition);

    /**
     * Refuses violations.
     *
     * @param violations the violations found
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_003 when a
     *         path is given by two packs not on one chain
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_034 when a
     *         rule of a mounted pack is not scoped to its folder, with the first such rule under "rule",
     *         its folder under "folder" and its pack under "pack"; KAY_CFG_031 when a pack replaces a
     *         locked item, with the first item under "path" and the pack that locks it under "pack";
     *         KAY_CFG_001 for any other violation. Each carries every violation under "violations"
     */
    void requireValid(List<Violation> violations);
}
