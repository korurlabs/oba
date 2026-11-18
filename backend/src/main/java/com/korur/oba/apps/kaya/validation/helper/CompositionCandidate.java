package com.korur.oba.apps.kaya.validation.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;

/**
 * What a repository delivers, as the caller composed it, handed to {@link CompositionRules}. The
 * composing domain builds it, so validation depends on no composition type of its own callers.
 *
 * @param repositoryId      the repository
 * @param files             one {@link PathCandidate} per delivered path
 * @param conflicts         one PATH_IN_TWO_SETS violation per path two owners give
 * @param settingsHooks     the hook commands .claude/settings.json registers; null when it is not
 *                          delivered or is not a JSON object
 * @param mounts            the packs mounted in folders of the repository, by folder
 * @param mountViolations   the mount, override and mount rule scope violations found while composing,
 *                          which need the rules' content
 */
public record CompositionCandidate(
        UUID repositoryId,
        List<PathCandidate> files,
        List<Violation> conflicts,
        List<HookRegistration> settingsHooks,
        List<MountCandidate> mounts,
        List<Violation> mountViolations
) {

    /**
     * The delivered paths by path.
     *
     * @return a map from path to its {@link PathCandidate}
     */
    public Map<String, PathCandidate> byPath() {
        return files.stream().collect(Collectors.toMap(PathCandidate::path, Function.identity()));
    }
}
