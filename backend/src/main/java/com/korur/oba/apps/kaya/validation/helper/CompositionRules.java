package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;

/**
 * The rules a repository's whole composition must keep, which no single file can be checked for:
 * each path from one pack, no two paths that differ only in letter case, the symlink
 * rule (no path below a link, every link ending at a delivered path inside the repository and the
 * allow-list, an always-on link ending at a delivered regular file), a settings file that still
 * registers the hook dispatcher the repository delivers, a settings file for the deny rules of every guardrail, the namespaces of the mounted packs,
 * no guardrail in a mounted pack, and the mount, override and mount rule violations the composer found. The part of the symlink rule a version can keep with its own links is also
 * checked on the version alone, so a pack that no repository uses yet is refused when it is
 * submitted.
 *
 * Used by {@link com.korur.oba.apps.kaya.validation.service.ValidationQueryService}.
 */
@Component
public class CompositionRules {

    /**
     * Checks a composition.
     *
     * @param composition the composition
     * @return its violations, each naming the repository; empty when it keeps every rule
     */
    public List<Violation> validate(CompositionCandidate composition) {
        List<Violation> violations = new ArrayList<>(composition.conflicts());
        violations.addAll(composition.mountViolations());
        violations.addAll(namespaceViolations(composition));
        for (Violation violation : caseViolations(composition.files().stream().map(PathCandidate::path).toList())) {
            violations.add(Violation.ofRepository(composition.repositoryId(), violation.path(), violation.rule(),
                    violation.message()));
        }
        Map<String, PathCandidate> byPath = composition.byPath();
        Map<String, String> links = composition.files().stream()
                .filter(file -> file.symlink() && file.symlinkTarget() != null)
                .collect(Collectors.toMap(PathCandidate::path, PathCandidate::symlinkTarget));
        for (PathCandidate file : composition.files()) {
            for (Violation violation : symlinkViolations(file.path(), file.symlink(), file.symlinkTarget(), links,
                    resolved -> isDelivered(resolved, byPath), resolved -> isDeliveredFile(resolved, byPath))) {
                violations.add(Violation.ofRepository(composition.repositoryId(), violation.path(), violation.rule(),
                        violation.message()));
            }
        }

        if (byPath.containsKey(ValidationConstant.DISPATCHER_PATH) && composition.settingsHooks() != null) {
            for (String event : ValidationConstant.DISPATCHER_EVENTS) {
                if (!registers(composition.settingsHooks(), event)) {
                    violations.add(Violation.ofRepository(composition.repositoryId(), ValidationConstant.SETTINGS_PATH,
                            ViolationRule.SETTINGS_DISPATCHER_MISSING,
                            String.format(ValidationConstant.SETTINGS_DISPATCHER_MISSING_MESSAGE, event)));
                }
            }
        }
        if (!byPath.containsKey(ValidationConstant.SETTINGS_PATH)) {
            for (PathCandidate file : composition.files()) {
                if (file.deny() != null && !file.deny().isEmpty()) {
                    violations.add(Violation.ofRepository(composition.repositoryId(), file.path(),
                            ViolationRule.GUARDRAIL_SETTINGS_MISSING, ValidationConstant.GUARDRAIL_SETTINGS_MISSING_MESSAGE));
                }
            }
        }
        return violations;
    }

    /**
     * The symlink rules a version must keep with its own links alone, before any repository composes
     * it: no two paths that differ only in letter case, no path below one of its links, and each of its links, followed through its other links,
     * ends inside the repository and on the allow-list. Whether a target is delivered depends on
     * the other packs, so a dangling link is left to the composition. A link whose target is
     * invalid or leaves the repository on its own is already refused by the file rules and is not
     * reported twice.
     *
     * @param files the version's files
     * @return their violations, by path; empty when its links keep the rules
     */
    public List<Violation> validateLinks(List<FileCandidate> files) {
        Map<String, String> links = files.stream()
                .filter(file -> file.symlink() && file.symlinkTarget() != null)
                .collect(Collectors.toMap(FileCandidate::path, FileCandidate::symlinkTarget, (first, second) -> first));
        List<Violation> violations = new ArrayList<>(caseViolations(files.stream().map(FileCandidate::path).toList()));
        for (FileCandidate file : files) {
            boolean refusedAlone = file.symlink()
                    && (!FileRules.isValidTarget(file.symlinkTarget())
                    || PathRules.resolveSymlink(file.path(), file.symlinkTarget()).isEmpty());
            if (refusedAlone) {
                PathRules.linkAbove(file.path(), links).ifPresent(link -> violations.add(throughLink(file.path(), link)));
                continue;
            }
            violations.addAll(symlinkViolations(file.path(), file.symlink(), file.symlinkTarget(), links, resolved -> true,
                    resolved -> true));
        }
        return violations;
    }

    /**
     * The symlink rule for one path, checked over the whole composition because the link and the
     * path it affects may come from different packs: no delivered path lies below a delivered link,
     * and every link, followed through the other delivered links as the file system follows them,
     * ends inside the repository, at a path on the allow-list or a directory a delivered path can lie
     * below (the tool link .claude/rules to .agents/rules), at a path the composition delivers as a file
     * or as a directory with files below it. The first part keeps the CLI from writing a file
     * through a link (.agents/skills/x -> ../../.git/hooks with .agents/skills/x/pre-commit); the
     * second keeps a link from leaving the repository through a chain or pointing anywhere Kaya
     * does not own. A chain longer than SYMLINK_MAX_HOPS is a loop and dangles. An always-on link
     * must end at a delivered regular file, never a directory, because Claude Code must load that
     * file at every start; the CLI refuses the same link when it delivers.
     */
    private static List<Violation> symlinkViolations(String path, boolean symlink, String target,
                                                           Map<String, String> links, Predicate<String> delivered,
                                                           Predicate<String> deliveredFile) {
        List<Violation> violations = new ArrayList<>();
        PathRules.linkAbove(path, links).ifPresent(link -> violations.add(throughLink(path, link)));
        if (!symlink || target == null) {
            return violations;
        }
        SymlinkResolution resolution = PathRules.resolveSymlink(path, target, links);
        if (resolution.outside()) {
            violations.add(Violation.ofPath(path, ViolationRule.SYMLINK_OUTSIDE_REPOSITORY,
                    ValidationConstant.SYMLINK_OUTSIDE_REPOSITORY_MESSAGE));
        } else if (resolution.loop()) {
            violations.add(danglingViolation(path, target));
        } else if (!PathRules.isAllowedPath(resolution.path()) && !PathRules.isAllowedDirectory(resolution.path())) {
            violations.add(Violation.ofPath(path, ViolationRule.SYMLINK_TARGET_NOT_ALLOWED,
                    String.format(ValidationConstant.SYMLINK_TARGET_NOT_ALLOWED_MESSAGE, resolution.path())));
        } else if (!delivered.test(resolution.path())) {
            violations.add(danglingViolation(path, target));
        } else if (PathRules.isAlwaysOnPath(path) && !deliveredFile.test(resolution.path())) {
            violations.add(Violation.ofPath(path, ViolationRule.SYMLINK_CONTENT_REQUIRED,
                    String.format(ValidationConstant.ALWAYS_ON_LINK_TARGET_MESSAGE, resolution.path())));
        }
        return violations;
    }

    /**
     * Every file of a mounted pack lies in that pack's namespace, so a mounted pack can neither
     * replace a path of the packs at the root nor reach settings, hooks, the tool links or another
     * mounted pack's files. A file below .agents/rules/guardrails/ is a guardrail and reported as
     * GUARDRAIL_SCOPE instead, even for a pack named guardrails, whose rule namespace is that folder:
     * a guardrail limits every session of the repository, which a pack mounted in one folder may not
     * decide.
     */
    private static List<Violation> namespaceViolations(CompositionCandidate composition) {
        Map<UUID, MountCandidate> mounts = composition.mounts().stream()
                .collect(Collectors.toMap(MountCandidate::packId, Function.identity()));
        List<Violation> violations = new ArrayList<>();
        for (PathCandidate file : composition.files()) {
            MountCandidate mount = mounts.get(file.ownerId());
            if (mount == null) {
                continue;
            }
            String name = mount.name();
            if (file.path().startsWith(ValidationConstant.GUARDRAILS_PREFIX)) {
                violations.add(Violation.ofRepository(composition.repositoryId(), file.path(), ViolationRule.GUARDRAIL_SCOPE,
                        String.format(ValidationConstant.GUARDRAIL_SCOPE_MESSAGE, name)));
                continue;
            }
            if (MountPaths.isInNamespace(file.path(), name, mount.rootPath())) {
                continue;
            }
            violations.add(Violation.ofRepository(composition.repositoryId(), file.path(),
                    ViolationRule.MOUNT_PATH_OUTSIDE_NAMESPACE,
                    String.format(ValidationConstant.MOUNT_PATH_OUTSIDE_NAMESPACE_MESSAGE, name, name, name, name,
                            MountPaths.agentsFile(mount.rootPath()))));
        }
        return violations;
    }

    private static List<Violation> caseViolations(List<String> paths) {
        return PathRules.caseConflicts(paths).entrySet().stream()
                .map(conflict -> Violation.ofPath(conflict.getKey(), ViolationRule.PATH_CASE_CONFLICT,
                        String.format(ValidationConstant.PATH_CASE_CONFLICT_MESSAGE, conflict.getValue())))
                .toList();
    }

    private static Violation throughLink(String path, String link) {
        return Violation.ofPath(path, ViolationRule.PATH_THROUGH_SYMLINK,
                String.format(ValidationConstant.PATH_THROUGH_SYMLINK_MESSAGE, link));
    }

    /** A resolved path is never a link itself, so a delivered file or a file below it is enough. */
    private static boolean isDelivered(String path, Map<String, PathCandidate> byPath) {
        if (byPath.containsKey(path)) {
            return true;
        }
        String directory = path + ValidationConstant.PATH_SEPARATOR;
        return byPath.keySet().stream().anyMatch(delivered -> delivered.startsWith(directory));
    }

    private static boolean isDeliveredFile(String path, Map<String, PathCandidate> byPath) {
        PathCandidate file = byPath.get(path);
        return file != null && !file.symlink();
    }

    /** Whether a hook of the event runs a command that names the hook dispatcher. */
    private static boolean registers(List<HookRegistration> hooks, String event) {
        return hooks.stream().anyMatch(hook -> hook.event().equals(event) && hook.command() != null
                && hook.command().contains(ValidationConstant.DISPATCHER_PATH));
    }

    private static Violation danglingViolation(String path, String target) {
        return Violation.ofPath(path, ViolationRule.SYMLINK_DANGLING,
                String.format(ValidationConstant.SYMLINK_DANGLING_MESSAGE, target));
    }
}
