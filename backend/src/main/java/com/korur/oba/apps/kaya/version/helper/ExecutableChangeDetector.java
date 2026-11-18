package com.korur.oba.apps.kaya.version.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.settings.helper.SettingsDocument;
import com.korur.oba.apps.kaya.settings.helper.SettingsParser;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.validation.helper.PathRules;
import com.korur.oba.apps.kaya.validation.helper.SymlinkResolution;

/**
 * Decides whether a version changes something that runs on a developer's machine, which only an
 * OWNER may approve: a hook script, a file with the executable bit, the hooks or permissions of the settings, a plugin file under .claude/skills that is not Markdown (also when it lies
 * elsewhere and a link under .claude/skills exposes it there), any link under .claude/skills, or
 * a link that ends at a hook script or under .claude/skills, or any guardrail, whose text limits
 * every session and whose deny rules become permissions. A change is any path
 * added, removed, or changed in content, mode or target against the base version.
 */
@Component
@RequiredArgsConstructor
public class ExecutableChangeDetector {

    private final SettingsParser settingsParser;

    /**
     * Compares a version with its base, reading the settings from the .claude/settings.json file of
     * each list.
     *
     * @param before the base version's rendered files; empty for an owner's first version
     * @param after  the version's rendered files
     * @return true when the change needs an OWNER's approval
     */
    public boolean isExecutableChange(List<RenderedFile> before, List<RenderedFile> after) {
        return isExecutableChange(before, after, settingsContent(before), settingsContent(after));
    }

    /**
     * Compares a version with its base, with the settings content given apart from the files.
     *
     * @param before         the base version's files; empty for an owner's first version
     * @param after          the version's files
     * @param settingsBefore the base's .claude/settings.json content; null when absent
     * @param settingsAfter  the version's .claude/settings.json content; null when absent
     * @return true when the change needs an OWNER's approval
     */
    public boolean isExecutableChange(List<RenderedFile> before, List<RenderedFile> after,
                                      byte[] settingsBefore, byte[] settingsAfter) {
        Map<String, RenderedFile> old = index(before);
        Map<String, RenderedFile> next = index(after);
        Map<String, String> oldLinks = links(before);
        Map<String, String> newLinks = links(after);
        Map<String, String> oldExposures = pluginExposures(oldLinks);
        Map<String, String> newExposures = pluginExposures(newLinks);
        Set<String> paths = new HashSet<>(old.keySet());
        paths.addAll(next.keySet());
        for (String path : paths) {
            RenderedFile oldFile = old.get(path);
            RenderedFile newFile = next.get(path);
            if (!changed(oldFile, newFile)) {
                continue;
            }
            if (PathRules.isHookPath(path) || PathRules.isGuardrailPath(path) || isExecutable(oldFile) || isExecutable(newFile)
                    || isPluginCode(path, oldFile) || isPluginCode(path, newFile)
                    || isPluginCode(exposedAs(path, oldExposures), oldFile)
                    || isPluginCode(exposedAs(path, newExposures), newFile)
                    || isLinkToCode(path, oldFile, oldLinks) || isLinkToCode(path, newFile, newLinks)) {
                return true;
            }
        }
        return settingsChanged(settingsBefore, settingsAfter);
    }

    private boolean settingsChanged(byte[] before, byte[] after) {
        if (before == null && after == null) {
            return false;
        }
        Optional<SettingsDocument> old = before == null ? Optional.empty() : settingsParser.parse(before);
        Optional<SettingsDocument> next = after == null ? Optional.empty() : settingsParser.parse(after);
        JsonNode oldHooks = old.map(SettingsDocument::hooksNode).orElse(null);
        JsonNode newHooks = next.map(SettingsDocument::hooksNode).orElse(null);
        JsonNode oldPermissions = old.map(SettingsDocument::permissions).orElse(null);
        JsonNode newPermissions = next.map(SettingsDocument::permissions).orElse(null);
        return !Objects.equals(oldHooks, newHooks) || !Objects.equals(oldPermissions, newPermissions);
    }

    private static boolean changed(RenderedFile oldFile, RenderedFile newFile) {
        if (oldFile == null || newFile == null) {
            return true;
        }
        return oldFile.mode() != newFile.mode()
                || !Objects.equals(oldFile.sha256(), newFile.sha256())
                || !Objects.equals(oldFile.symlinkTarget(), newFile.symlinkTarget());
    }

    private static boolean isExecutable(RenderedFile file) {
        return file != null && file.mode() == FileMode.EXECUTABLE;
    }

    /**
     * A plugin file under .claude/skills that is not Markdown, or any link there: a link's name
     * does not tell what it points at, so adding, removing or retargeting one can swap the code a
     * plugin runs.
     */
    private static boolean isPluginCode(String path, RenderedFile file) {
        return file != null && path != null && PathRules.isPluginPath(path)
                && (file.mode() == FileMode.SYMLINK || !path.endsWith(ValidationConstant.MARKDOWN_SUFFIX));
    }

    /**
     * A link elsewhere that ends, through the version's own links, at a hook script or under
     * .claude/skills runs that code under another name. A link that cannot be resolved is refused
     * by the symlink rules; it counts here too, so that it is never approved as harmless.
     */
    private static boolean isLinkToCode(String path, RenderedFile file, Map<String, String> links) {
        if (file == null || file.mode() != FileMode.SYMLINK) {
            return false;
        }
        SymlinkResolution resolution = PathRules.resolveSymlink(path, file.symlinkTarget(), links);
        if (resolution.path() == null) {
            return true;
        }
        String target = resolution.path() + ValidationConstant.PATH_SEPARATOR;
        return target.startsWith(ValidationConstant.HOOKS_PREFIX) || target.startsWith(ValidationConstant.CLAUDE_SKILLS_PREFIX);
    }

    /**
     * Where the version's links under .claude/skills end, mapped to the link: a mirror link
     * .claude/skills/x -> ../../.agents/skills/x makes .agents/skills/x a plugin directory, so its
     * files run as plugin code although they lie under .agents/skills.
     */
    private static Map<String, String> pluginExposures(Map<String, String> links) {
        Map<String, String> exposures = new HashMap<>();
        for (Map.Entry<String, String> link : links.entrySet()) {
            if (!PathRules.isPluginPath(link.getKey())) {
                continue;
            }
            String resolved = PathRules.resolveSymlink(link.getKey(), link.getValue(), links).path();
            if (resolved != null) {
                exposures.putIfAbsent(resolved, link.getKey());
            }
        }
        return exposures;
    }

    /**
     * The path under .claude/skills at which a path outside it is seen through one of the links,
     * such as .claude/skills/x/run.sh for .agents/skills/x/run.sh; null when no link exposes it.
     */
    private static String exposedAs(String path, Map<String, String> exposures) {
        for (Map.Entry<String, String> exposure : exposures.entrySet()) {
            String target = exposure.getKey();
            if (path.equals(target)) {
                return exposure.getValue();
            }
            if (path.startsWith(target + ValidationConstant.PATH_SEPARATOR)) {
                return exposure.getValue() + path.substring(target.length());
            }
        }
        return null;
    }

    private static Map<String, String> links(List<RenderedFile> files) {
        return files.stream()
                .filter(file -> file.mode() == FileMode.SYMLINK && file.symlinkTarget() != null)
                .collect(Collectors.toMap(RenderedFile::path, RenderedFile::symlinkTarget));
    }

    private static Map<String, RenderedFile> index(List<RenderedFile> files) {
        return files.stream().collect(Collectors.toMap(RenderedFile::path, Function.identity()));
    }

    private static byte[] settingsContent(List<RenderedFile> files) {
        return files.stream()
                .filter(file -> PathRules.isSettingsFile(file.path()) && file.mode() != FileMode.SYMLINK)
                .map(RenderedFile::content)
                .findFirst()
                .orElse(null);
    }
}
