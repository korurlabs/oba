package com.korur.oba.apps.kaya.release.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.release.dto.embedded.PackVersionEntry;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.helper.CompositionCandidate;
import com.korur.oba.apps.kaya.validation.helper.HookRegistration;
import com.korur.oba.apps.kaya.validation.helper.MountCandidate;
import com.korur.oba.apps.kaya.version.entity.Version;

/**
 * What a repository delivers, built by {@link Composer}: the packs at the root with their ancestors,
 * by depth and then name, then each mounted pack by folder, each at its current version unless the
 * caller replaced it for a preview.
 *
 * @param repositoryId      the repository
 * @param files             one {@link ComposedFile} per path, by path code point by code point; of
 *                          the packs at the root on one chain the deepest gives a path, and a path
 *                          in conflict is kept from the last composed pack
 * @param conflicts         one PATH_IN_TWO_SETS violation per path two packs not on one chain give
 * @param versions          the composed {@link Version}s, in composition order
 * @param settingsHooks     the hook commands .claude/settings.json registers; null when it is not
 *                          delivered or is not a JSON object
 * @param mounts            the packs mounted in folders of the repository, by folder
 * @param mountViolations   the mount, override and mount rule scope violations, which need the
 *                          composed tree or the rules' content and are therefore found while composing
 * @param packVersions      every composed pack with its version and folder, in composition order
 * @param contents          the bytes of every composed regular file, narrowed copies included, by
 *                          sha256; a release stores those no blob holds yet
 */
public record Composition(
        UUID repositoryId,
        List<ComposedFile> files,
        List<Violation> conflicts,
        List<Version> versions,
        List<HookRegistration> settingsHooks,
        List<MountCandidate> mounts,
        List<Violation> mountViolations,
        List<PackVersionEntry> packVersions,
        Map<String, byte[]> contents
) {

    /**
     * The composed paths by path.
     *
     * @return a map from path to its {@link ComposedFile}
     */
    public Map<String, ComposedFile> byPath() {
        return files.stream().collect(Collectors.toMap(ComposedFile::path, Function.identity()));
    }

    /**
     * The composition as the composition rules of validation read it.
     *
     * @return the {@link CompositionCandidate} of this composition
     */
    public CompositionCandidate toCandidate() {
        return new CompositionCandidate(repositoryId, files.stream().map(ComposedFile::toCandidate).toList(),
                conflicts, settingsHooks, mounts, mountViolations);
    }
}
