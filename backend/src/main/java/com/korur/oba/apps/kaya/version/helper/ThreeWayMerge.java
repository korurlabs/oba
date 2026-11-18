package com.korur.oba.apps.kaya.version.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

// 4. Internal
import com.korur.oba.apps.kaya.version.enums.FileChange;

/**
 * Moves a draft onto a newer version of its set, path by path, against the base both started
 * from. A path only the current version changed takes the current version's state, a path only
 * the draft changed keeps the draft's, a path both changed the same way is settled, and a path both
 * changed differently is a conflict the proposer resolves. Paths are compared by their state alone
 * (content, mode and link target), so no content is read.
 */
public final class ThreeWayMerge {

    private ThreeWayMerge() {
    }

    /**
     * The result of a merge; a path in neither set keeps the draft's state.
     *
     * @param takeCurrent the paths that take the current version's state, its deletion included
     * @param conflicts   the paths both sides changed differently, by path
     */
    public record Outcome(Set<String> takeCurrent, Set<String> conflicts) {
    }

    /**
     * Merges by path.
     *
     * @param base    the state of each path in the draft's base; a path it lacks is absent
     * @param draft   the state of each path in the draft
     * @param current the state of each path in the set's current version
     * @param <K>     a state that is equal exactly when the files are the same
     * @return which paths take the current version's state and which conflict
     */
    public static <K> Outcome merge(Map<String, K> base, Map<String, K> draft, Map<String, K> current) {
        Set<String> paths = new TreeSet<>();
        paths.addAll(base.keySet());
        paths.addAll(draft.keySet());
        paths.addAll(current.keySet());
        Set<String> takeCurrent = new TreeSet<>();
        Set<String> conflicts = new TreeSet<>();
        for (String path : paths) {
            K before = base.get(path);
            K ours = draft.get(path);
            K theirs = current.get(path);
            if (Objects.equals(ours, theirs) || Objects.equals(theirs, before)) {
                continue;
            }
            if (Objects.equals(ours, before)) {
                takeCurrent.add(path);
            } else {
                conflicts.add(path);
            }
        }
        return new Outcome(takeCurrent, conflicts);
    }

    /**
     * What one side did to a path since the base.
     *
     * @param before the state in the base; null when absent
     * @param after  the state on that side; null when absent
     * @param <K>    the state type
     * @return the change; null when the side left the path as it was
     */
    public static <K> FileChange change(K before, K after) {
        if (Objects.equals(before, after)) {
            return null;
        }
        if (before == null) {
            return FileChange.ADDED;
        }
        return after == null ? FileChange.DELETED : FileChange.MODIFIED;
    }
}
