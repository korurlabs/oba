package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;

// 4. Internal
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.validation.helper.FileCandidate;
import com.korur.oba.apps.kaya.validation.helper.FrontmatterFacts;
import com.korur.oba.apps.kaya.validation.helper.PathRules;

/**
 * Builds the {@link FileCandidate} the validation rules check from a file and its {@link FileMode},
 * reading the frontmatter of a SKILL.md with {@link FrontmatterParser}, so the validation domain needs
 * neither the file mode nor a frontmatter reader of its own.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class FileCandidates {

    private FileCandidates() {
    }

    /**
     * Builds the candidate of one file.
     *
     * @param path          the path relative to the repository root
     * @param mode          FILE, EXECUTABLE or SYMLINK; null counts as a file, which the content
     *                      rules then hold to its content
     * @param symlinkTarget the target of a symlink; null otherwise
     * @param content       the bytes of a file; null for a symlink, or when only paths are checked
     * @return the candidate, with the frontmatter facts of a SKILL.md that has content
     */
    public static FileCandidate of(String path, FileMode mode, String symlinkTarget, byte[] content) {
        boolean symlink = mode == FileMode.SYMLINK;
        FrontmatterFacts facts = !symlink && content != null && path != null && PathRules.isSkillFile(path)
                ? facts(FrontmatterParser.parse(new String(content, StandardCharsets.UTF_8)))
                : FrontmatterFacts.NONE;
        return new FileCandidate(path, symlink, symlinkTarget, content, facts);
    }

    /**
     * The facts the skill rules read from a parsed frontmatter.
     *
     * @param frontmatter the parsed frontmatter
     * @return its error, keys, description and when_to_use
     */
    public static FrontmatterFacts facts(Frontmatter frontmatter) {
        return new FrontmatterFacts(frontmatter.error(), frontmatter.values().keySet(),
                frontmatter.text(RenderConstant.FRONTMATTER_DESCRIPTION),
                frontmatter.text(RenderConstant.FRONTMATTER_WHEN_TO_USE));
    }
}
