package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;

/**
 * The rules one file must keep on its own, whatever repository delivers it: the path allow-list,
 * a symlink that stays inside the repository, the size limit, the skill rules of the old policy
 * check (frontmatter keys, description length, line count, never a symlink) and no symlink at the
 * settings file or a hook. Where a link leads through the other delivered links is a composition
 * rule. Whether the settings file is a JSON object is checked where it is parsed, by the render
 * domain, which refuses it as an unreadable file.
 */
@Component
public class FileRules {

    /**
     * Checks one file.
     *
     * @param file the file
     * @return its violations; empty when it keeps every rule
     */
    public List<Violation> validate(FileCandidate file) {
        List<Violation> violations = new ArrayList<>();
        String path = file.path();
        if (!PathRules.isAllowedPath(path)) {
            violations.add(Violation.ofPath(path, ViolationRule.PATH_NOT_ALLOWED,
                    ValidationConstant.PATH_NOT_ALLOWED_MESSAGE));
            return violations;
        }
        if (file.symlink()) {
            validateSymlink(file, violations);
        } else {
            validateContent(file, violations);
        }
        return violations;
    }

    private void validateSymlink(FileCandidate file, List<Violation> violations) {
        String path = file.path();
        String target = file.symlinkTarget();
        if (file.content() != null) {
            violations.add(Violation.ofPath(path, ViolationRule.MODE_INVALID,
                    ValidationConstant.SYMLINK_WITH_CONTENT_MESSAGE));
        }
        if (PathRules.isSkillFile(path)) {
            violations.add(Violation.ofPath(path, ViolationRule.SKILL_SYMLINK, ValidationConstant.SKILL_SYMLINK_MESSAGE));
        }
        // A changed hook script needs an owner; a link there would let an ordinary change of the
        // file it points at change the code the hook runs.
        if (PathRules.isHookPath(path)) {
            violations.add(Violation.ofPath(path, ViolationRule.HOOK_SYMLINK, ValidationConstant.HOOK_SYMLINK_MESSAGE));
        }
        // The settings checks read the file's own bytes, and the CLI writes it as a regular file.
        // An always-on link is allowed; the composition holds it to a delivered regular file.
        if (PathRules.requiresContent(path)) {
            violations.add(Violation.ofPath(path, ViolationRule.SYMLINK_CONTENT_REQUIRED,
                    ValidationConstant.SYMLINK_CONTENT_REQUIRED_MESSAGE));
        }
        if (!isValidTarget(target)) {
            violations.add(Violation.ofPath(path, ViolationRule.SYMLINK_TARGET_INVALID,
                    ValidationConstant.SYMLINK_TARGET_INVALID_MESSAGE));
            return;
        }
        if (PathRules.resolveSymlink(path, target).isEmpty()) {
            violations.add(Violation.ofPath(path, ViolationRule.SYMLINK_OUTSIDE_REPOSITORY,
                    ValidationConstant.SYMLINK_OUTSIDE_REPOSITORY_MESSAGE));
        }
    }

    /** Whether a link target is relative, non-blank, at most 500 characters and has no backslash. */
    static boolean isValidTarget(String target) {
        return target != null && !target.isBlank() && !target.startsWith("/") && !target.contains("\\")
                && target.length() <= ValidationConstant.PATH_MAX_LENGTH;
    }

    private void validateContent(FileCandidate file, List<Violation> violations) {
        String path = file.path();
        if (file.content() == null || file.symlinkTarget() != null) {
            violations.add(Violation.ofPath(path, ViolationRule.MODE_INVALID,
                    ValidationConstant.FILE_WITHOUT_CONTENT_MESSAGE));
            return;
        }
        if (file.content().length > ValidationConstant.FILE_MAX_BYTES) {
            violations.add(Violation.ofPath(path, ViolationRule.FILE_TOO_LARGE,
                    String.format(ValidationConstant.FILE_TOO_LARGE_MESSAGE, file.content().length, ValidationConstant.FILE_MAX_BYTES)));
            return;
        }
        if (PathRules.isSkillFile(path)) {
            validateSkill(path, new String(file.content(), StandardCharsets.UTF_8), frontmatterOf(file), violations);
        }
    }

    private void validateSkill(String path, String text, FrontmatterFacts frontmatter, List<Violation> violations) {
        long lines = lineCount(text);
        if (lines >= ValidationConstant.SKILL_MAX_LINES) {
            violations.add(Violation.ofPath(path, ViolationRule.SKILL_TOO_LONG,
                    String.format(ValidationConstant.SKILL_TOO_LONG_MESSAGE, lines, ValidationConstant.SKILL_MAX_LINES)));
        }
        if (frontmatter.error() != null) {
            violations.add(Violation.ofPath(path, ViolationRule.FRONTMATTER_INVALID,
                    String.format(ValidationConstant.FRONTMATTER_INVALID_MESSAGE, frontmatter.error())));
            return;
        }
        for (String key : frontmatter.keys()) {
            if (!ValidationConstant.SKILL_FRONTMATTER_KEYS.contains(key)) {
                violations.add(Violation.ofPath(path, ViolationRule.FRONTMATTER_KEY_UNKNOWN,
                        String.format(ValidationConstant.FRONTMATTER_KEY_UNKNOWN_MESSAGE, key)));
            }
        }
        int listingLength = listingLength(frontmatter);
        if (listingLength > ValidationConstant.SKILL_LISTING_MAX_CHARACTERS) {
            violations.add(Violation.ofPath(path, ViolationRule.SKILL_DESCRIPTION_TOO_LONG,
                    String.format(ValidationConstant.SKILL_DESCRIPTION_TOO_LONG_MESSAGE, listingLength,
                            ValidationConstant.SKILL_LISTING_MAX_CHARACTERS)));
        }
    }

    /**
     * The quantity a size limit measures in a file: bytes for FILE_TOO_LARGE, lines for
     * SKILL_TOO_LONG, description plus when_to_use characters for SKILL_DESCRIPTION_TOO_LONG. A
     * waiver for a file that already broke a limit compares this measure, so a seeded skill over
     * the line limit may be edited but gain no line.
     *
     * @param file the file with its content
     * @param rule a one-file size limit
     * @return the measure; 0 for a file without content, and for a rule that is not such a limit
     */
    public long measure(FileCandidate file, ViolationRule rule) {
        if (file.content() == null) {
            return 0;
        }
        return switch (rule) {
            case FILE_TOO_LARGE -> file.content().length;
            case SKILL_TOO_LONG -> lineCount(new String(file.content(), StandardCharsets.UTF_8));
            case SKILL_DESCRIPTION_TOO_LONG -> listingLength(frontmatterOf(file));
            default -> 0;
        };
    }

    private static int listingLength(FrontmatterFacts frontmatter) {
        return codePoints(frontmatter.description()) + codePoints(frontmatter.whenToUse());
    }

    private static FrontmatterFacts frontmatterOf(FileCandidate file) {
        return file.frontmatter() == null ? FrontmatterFacts.NONE : file.frontmatter();
    }

    /** Lines as an editor counts them: a final newline does not start another line. */
    private static long lineCount(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        long newlines = text.chars().filter(character -> character == '\n').count();
        return text.endsWith("\n") ? newlines : newlines + 1;
    }

    private static int codePoints(String text) {
        return Optional.ofNullable(text).map(value -> value.codePointCount(0, value.length())).orElse(0);
    }
}
