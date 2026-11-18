package com.korur.oba.apps.kaya.release.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.release.constant.ReleaseConstant;
import com.korur.oba.apps.kaya.release.dto.embedded.PackVersionEntry;
import com.korur.oba.apps.kaya.release.repository.RepositoryPackRepository;
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.helper.DeliveryPaths;
import com.korur.oba.apps.kaya.render.helper.Frontmatter;
import com.korur.oba.apps.kaya.render.helper.FrontmatterParser;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.settings.helper.SettingsParser;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.validation.enums.PatternClass;
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;
import com.korur.oba.apps.kaya.validation.helper.HookRegistration;
import com.korur.oba.apps.kaya.validation.helper.MountCandidate;
import com.korur.oba.apps.kaya.validation.helper.MountPaths;
import com.korur.oba.apps.kaya.validation.helper.PathRules;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;

/**
 * Builds the {@link Composition} a repository delivers from its packs at the root, its mounted
 * packs and the organization's required packs.
 *
 * The packs at the root are the chains of the repository's root packs and of the required packs,
 * plus the ancestors of each mounted pack, composed by depth and then name. Of the packs that give
 * one path the deepest wins, as long as they all lie on one chain; packs on two branches of the
 * tree giving one path are a PATH_IN_TWO_SETS conflict, since neither is more specific. Each
 * mounted pack is then composed by folder: its files keep their stored paths, which its namespace
 * keeps apart from the root packs' paths, except its AGENTS.md, which is delivered in the mount
 * folder. A mounted pack that is also composed at the root is PACK_MOUNT_INHERITED and composed
 * there only.
 *
 * Each pack is composed at its current version, the newest approved one, unless the caller
 * replaces it for a preview; nothing pins an older version. Every version is rendered through
 * {@link VersionQueryService}, so the composed bytes are the stored file bytes. A rule's
 * frontmatter decides whether it is always-on and how a mounted pack's override narrows it, a
 * guardrail's frontmatter gives the deny rules its manifest entry carries, and the settings decide
 * whether the hook dispatcher is registered.
 *
 * Used by {@link com.korur.oba.apps.kaya.release.service.ReleaseService},
 * {@link com.korur.oba.apps.kaya.release.service.ReleaseQueryService} and
 * {@link com.korur.oba.apps.kaya.release.service.RepositoryPackService}.
 */
@Component
@RequiredArgsConstructor
public class Composer {

    private final RepositoryPackRepository repositoryPackRepository;
    private final RepositoryQueryService repositoryQueryService;
    private final PackQueryService packQueryService;
    private final VersionQueryService versionQueryService;
    private final SettingsParser settingsParser;

    // One file a pack at the root gives, before the deepest pack of its path is chosen.
    private record Candidate(UUID packId, UUID versionId, RenderedFile file) {
    }

    /**
     * Composes a repository from its stored packs.
     *
     * @param organizationId the organization of the repository
     * @param repositoryId   the repository
     * @param replaced       version ids to compose instead of a pack's current one, by pack id;
     *                       empty for what the repository delivers now
     * @return the composition
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such repository or a replaced version
     */
    public Composition compose(UUID organizationId, UUID repositoryId, Map<UUID, UUID> replaced) {
        List<PackMount> mounts = repositoryPackRepository.findByRepositoryIdOrderByPositionAsc(repositoryId).stream()
                .map(row -> new PackMount(row.getPackId(), row.getRootPath()))
                .toList();
        return compose(organizationId, repositoryId, mounts, replaced);
    }

    /**
     * Composes a repository from the given packs, as a pack list change would leave it. The
     * organization's required packs are composed at the root as well, and a required pack the list
     * also names at the root is composed once.
     *
     * @param organizationId the organization of the repository
     * @param repositoryId   the repository
     * @param mounts         the packs and their mount folders; ids the organization does not have
     *                       are left out, the caller has resolved them
     * @param replaced       version ids to compose instead of a pack's current one, by pack id
     * @return the composition
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such repository or a replaced version
     */
    public Composition compose(UUID organizationId, UUID repositoryId, List<PackMount> mounts, Map<UUID, UUID> replaced) {
        // Refuses a repository the organization does not have before any pack is read.
        repositoryQueryService.getEntityById(organizationId, repositoryId);
        Set<UUID> listed = new LinkedHashSet<>(packQueryService.getRequiredPackIds(organizationId));
        mounts.forEach(mount -> listed.add(mount.packId()));
        Set<UUID> known = packQueryService.getEntitiesByIds(organizationId, listed).stream()
                .map(Pack::getId)
                .collect(Collectors.toSet());

        // A required pack sets limits for every repository, so it is composed at the root whatever
        // the repository's own list says.
        Tree tree = new Tree();
        for (UUID packId : packQueryService.getRequiredPackIds(organizationId)) {
            if (known.contains(packId)) {
                tree.addAtRoot(packQueryService.getChain(organizationId, packId));
            }
        }
        List<PackMount> folders = new ArrayList<>();
        for (PackMount mount : mounts) {
            if (!known.contains(mount.packId())) {
                continue;
            }
            List<Pack> chain = packQueryService.getChain(organizationId, mount.packId());
            if (mount.mounted()) {
                tree.add(chain);
                tree.addAtRoot(chain.subList(0, chain.size() - 1));
                folders.add(mount);
            } else {
                tree.addAtRoot(chain);
            }
        }

        List<Violation> mountViolations = new ArrayList<>();
        Map<UUID, MountCandidate> mounted = new LinkedHashMap<>();
        for (PackMount mount : folders.stream().sorted(Comparator.comparing(PackMount::rootPath)).toList()) {
            if (tree.isAtRoot(mount.packId())) {
                mountViolations.add(new Violation(null, ViolationRule.PACK_MOUNT_INHERITED,
                        ReleaseConstant.PACK_MOUNT_INHERITED_MESSAGE, repositoryId, List.of(mount.packId())));
                continue;
            }
            mounted.putIfAbsent(mount.packId(),
                    new MountCandidate(mount.packId(), tree.pack(mount.packId()).getName(), mount.rootPath()));
        }
        List<MountCandidate> mountList = List.copyOf(mounted.values());

        Map<String, ComposedFile> byPath = new LinkedHashMap<>();
        Map<String, Set<UUID>> conflictOwners = new LinkedHashMap<>();
        Map<String, byte[]> contents = new LinkedHashMap<>();
        List<Version> versions = new ArrayList<>();
        List<PackVersionEntry> packVersions = new ArrayList<>();

        Map<String, List<Candidate>> candidates = new LinkedHashMap<>();
        for (Pack pack : tree.rootOrder()) {
            UUID versionId = replaced.getOrDefault(pack.getId(), pack.getCurrentVersionId());
            packVersions.add(new PackVersionEntry(pack.getId(), versionId, null));
            if (versionId == null) {
                continue;
            }
            Version version = versionQueryService.getEntityById(organizationId, versionId);
            versions.add(version);
            for (RenderedFile file : versionQueryService.render(organizationId, version)) {
                candidates.computeIfAbsent(file.path(), path -> new ArrayList<>()).add(new Candidate(pack.getId(), version.getId(), file));
                keepContent(file, contents);
            }
        }
        mountViolations.addAll(lockViolations(repositoryId, tree, candidates));
        for (Map.Entry<String, List<Candidate>> entry : candidates.entrySet()) {
            List<Candidate> givers = entry.getValue();
            // Packs are composed by depth, so the last giver is the deepest; every other giver must
            // be one of its ancestors for it to be the more specific file.
            Candidate deepest = givers.get(givers.size() - 1);
            Set<UUID> ancestors = tree.ancestors(deepest.packId());
            if (givers.stream().anyMatch(giver -> giver != deepest && !ancestors.contains(giver.packId()))) {
                conflictOwners.put(entry.getKey(), givers.stream().map(Candidate::packId).collect(Collectors.toCollection(HashSet::new)));
            }
            byPath.put(entry.getKey(), composed(entry.getKey(), deepest.file(), deepest.packId(), deepest.versionId()));
        }

        for (MountCandidate mount : mountList) {
            Pack pack = tree.pack(mount.packId());
            UUID versionId = replaced.getOrDefault(pack.getId(), pack.getCurrentVersionId());
            packVersions.add(new PackVersionEntry(pack.getId(), versionId, mount.rootPath()));
            if (versionId == null) {
                continue;
            }
            Version version = versionQueryService.getEntityById(organizationId, versionId);
            versions.add(version);
            for (RenderedFile file : versionQueryService.render(organizationId, version)) {
                String path = mountedPath(file, mount.rootPath());
                ComposedFile first = byPath.get(path);
                if (first != null) {
                    conflictOwners.computeIfAbsent(path, key -> new HashSet<>(List.of(first.ownerId()))).add(pack.getId());
                    continue;
                }
                byPath.put(path, composed(path, file, pack.getId(), version.getId()));
                keepContent(file, contents);
            }
        }

        mountViolations.addAll(ruleScopeViolations(repositoryId, mountList, byPath, contents));
        Map<String, byte[]> narrowedContents = new LinkedHashMap<>();
        mountViolations.addAll(applyOverrides(repositoryId, mountList, byPath, contents, narrowedContents));
        contents.putAll(narrowedContents);

        List<ComposedFile> files = byPath.values().stream()
                .map(file -> withAlwaysOn(file, contents))
                .map(file -> withDeny(file, contents))
                .sorted(Comparator.comparing(ComposedFile::path))
                .toList();
        List<Violation> conflicts = conflictOwners.entrySet().stream()
                .map(entry -> new Violation(entry.getKey(), ViolationRule.PATH_IN_TWO_SETS,
                        ReleaseConstant.PATH_IN_TWO_OWNERS_MESSAGE, repositoryId,
                        entry.getValue().stream().sorted().toList()))
                .toList();
        ComposedFile settings = byPath.get(ValidationConstant.SETTINGS_PATH);
        byte[] settingsContent = settings == null || settings.sha256() == null ? null : contents.get(settings.sha256());
        List<HookRegistration> settingsHooks = settingsContent == null ? null : settingsParser.parse(settingsContent)
                .map(document -> document.hooks().stream()
                        .map(hook -> new HookRegistration(hook.event(), hook.command()))
                        .toList())
                .orElse(null);
        Set<String> delivered = files.stream().map(ComposedFile::sha256).collect(Collectors.toSet());
        contents.keySet().retainAll(delivered);
        return new Composition(repositoryId, files, conflicts, versions, settingsHooks, mountList,
                mountViolations, List.copyOf(packVersions), contents);
    }

    /**
     * A pack at the root that gives an item one of its ancestors gives replaces it, which a locked
     * item does not allow: every item of a required ancestor, and every guardrail. Items are
     * compared whole, so a file added to a locked skill replaces it too. Givers on two branches
     * are a conflict, not an override, and are left to PATH_IN_TWO_SETS.
     */
    private static List<Violation> lockViolations(UUID repositoryId, Tree tree, Map<String, List<Candidate>> candidates) {
        Map<String, Set<UUID>> giversByItem = new LinkedHashMap<>();
        for (Map.Entry<String, List<Candidate>> entry : candidates.entrySet()) {
            Set<UUID> givers = giversByItem.computeIfAbsent(PathRules.itemOf(entry.getKey()), item -> new LinkedHashSet<>());
            entry.getValue().forEach(candidate -> givers.add(candidate.packId()));
        }
        List<Violation> violations = new ArrayList<>();
        for (Map.Entry<String, Set<UUID>> entry : giversByItem.entrySet()) {
            for (UUID giver : entry.getValue()) {
                for (UUID other : entry.getValue()) {
                    if (!tree.ancestors(other).contains(giver)) {
                        continue;
                    }
                    Pack ancestor = tree.pack(giver);
                    boolean required = Boolean.TRUE.equals(ancestor.getRequired());
                    if (PathRules.isLocked(entry.getKey(), required)) {
                        violations.add(lockViolation(repositoryId, entry.getKey(), ancestor, required, other));
                    }
                }
            }
        }
        return violations;
    }

    /**
     * The OVERRIDE_LOCKED violation of a pack that gives an item a locked ancestor gives.
     *
     * @param repositoryId the repository whose composition delivers it; null for a check of the pack alone
     * @param item         the item
     * @param ancestor     the ancestor that locks it
     * @param required     whether the ancestor is required; false means the item is a guardrail
     * @param overriding   the descendant that gives it as well
     * @return the violation, naming the ancestor first and the descendant second
     */
    public static Violation lockViolation(UUID repositoryId, String item, Pack ancestor, boolean required, UUID overriding) {
        String message = String.format(required ? ReleaseConstant.OVERRIDE_LOCKED_REQUIRED_MESSAGE
                : ReleaseConstant.OVERRIDE_LOCKED_GUARDRAIL_MESSAGE, item, ancestor.getName());
        return new Violation(item, ViolationRule.OVERRIDE_LOCKED, message, repositoryId,
                List.of(ancestor.getId(), overriding));
    }

    /**
     * The path a mounted pack's file is delivered at: its AGENTS.md in the mount folder, where a
     * session working there loads it, and every other file at its stored path. A link stored at
     * AGENTS.md is a tool link and stays where it is, so the namespace check refuses it.
     */
    private static String mountedPath(RenderedFile file, String rootPath) {
        if (file.mode() == FileMode.SYMLINK || !file.path().equals(RenderConstant.AGENTS_FILE)) {
            return file.path();
        }
        return DeliveryPaths.agentsFile(AgentsFilePlacement.MOUNT_ROOT, rootPath).orElse(file.path());
    }

    private static ComposedFile composed(String path, RenderedFile file, UUID packId, UUID versionId) {
        Integer size = file.content() == null ? null : file.content().length;
        return new ComposedFile(path, file.mode(), file.sha256(), size, file.symlinkTarget(), false, packId, versionId,
                file.overridesPath(), null, null);
    }

    private static void keepContent(RenderedFile file, Map<String, byte[]> contents) {
        if (file.content() != null && file.sha256() != null) {
            contents.putIfAbsent(file.sha256(), file.content());
        }
    }

    /**
     * The packs a composition reaches, with the depth and the ancestors of each, read from the
     * chains {@link PackQueryService#getChain} gives root first.
     */
    private static final class Tree {

        private final Map<UUID, Pack> packs = new LinkedHashMap<>();
        private final Map<UUID, Set<UUID>> ancestors = new LinkedHashMap<>();
        private final Set<UUID> atRoot = new LinkedHashSet<>();

        void add(List<Pack> chain) {
            Set<UUID> above = new LinkedHashSet<>();
            for (Pack pack : chain) {
                packs.putIfAbsent(pack.getId(), pack);
                ancestors.putIfAbsent(pack.getId(), Set.copyOf(above));
                above.add(pack.getId());
            }
        }

        void addAtRoot(List<Pack> chain) {
            add(chain);
            chain.forEach(pack -> atRoot.add(pack.getId()));
        }

        boolean isAtRoot(UUID packId) {
            return atRoot.contains(packId);
        }

        Pack pack(UUID packId) {
            return packs.get(packId);
        }

        Set<UUID> ancestors(UUID packId) {
            return ancestors.getOrDefault(packId, Set.of());
        }

        /** The packs at the root by depth, then name, so an ancestor always comes before its descendants. */
        List<Pack> rootOrder() {
            return atRoot.stream()
                    .map(packs::get)
                    .sorted(Comparator.<Pack>comparingInt(pack -> ancestors(pack.getId()).size()).thenComparing(Pack::getName))
                    .toList();
        }
    }

    /**
     * A rule of a mounted pack must be path-scoped to its mount folder: every pattern, each brace
     * expansion on its own, lies under the folder. A rule loaded for files elsewhere, or at every
     * start, would change sessions that never touch the folder.
     */
    private static List<Violation> ruleScopeViolations(UUID repositoryId, List<MountCandidate> mounts,
                                                       Map<String, ComposedFile> byPath, Map<String, byte[]> contents) {
        Map<UUID, MountCandidate> byId = mountsById(mounts);
        List<Violation> violations = new ArrayList<>();
        for (ComposedFile file : byPath.values()) {
            MountCandidate mount = byId.get(file.ownerId());
            if (mount == null) {
                continue;
            }
            byte[] content = file.sha256() == null ? null : contents.get(file.sha256());
            ruleScopeViolation(repositoryId, mount, file.path(), file.mode(), content).ifPresent(violations::add);
        }
        return violations;
    }

    /**
     * Checks one file of a mounted pack against its mount folder. Only a rule in the pack's
     * namespace is checked; a file elsewhere is refused by the namespace check. The rule is read,
     * never rewritten: its stored bytes are delivered as written or refused.
     *
     * @param repositoryId the repository whose composition delivers it; null for a check of the pack alone
     * @param mount        the mounted pack and its folder
     * @param path         the stored path
     * @param mode         the file mode; a symlink has no frontmatter and so no paths
     * @param content      the file bytes; null for a symlink
     * @return the MOUNT_RULE_SCOPE violation, or empty when the file is no rule of the namespace or is scoped
     */
    public static Optional<Violation> ruleScopeViolation(UUID repositoryId, MountCandidate mount, String path, FileMode mode,
                                                         byte[] content) {
        // A guardrail of a pack named guardrails lies in that pack's rule namespace, but it is no
        // path-scoped rule: GUARDRAIL_SCOPE refuses it in a mounted pack instead.
        if (!MountPaths.isMountRule(path, mount.name()) || !PathRules.isScopableRule(path)) {
            return Optional.empty();
        }
        List<String> patterns = mode == FileMode.SYMLINK ? List.of() : rulePatterns(content);
        if (MountPaths.isScopedTo(patterns, mount.rootPath())) {
            return Optional.empty();
        }
        return Optional.of(Violation.ofMountRule(repositoryId, path, mount.packId(), mount.rootPath(),
                String.format(ReleaseConstant.MOUNT_RULE_SCOPE_MESSAGE, path, mount.name(), mount.rootPath())));
    }

    /**
     * Applies the override markers of the mounted packs' files. A mounted pack's doc that
     * overrides an inherited doc is delivered beside it, since only the pack's rules that cite it
     * make it effective. A mounted pack's rule that overrides an inherited rule narrows that rule:
     * its patterns under the mount folder are removed, so files there load the pack's version only, and a rule left
     * with no pattern is not delivered. An always-on or spanning inherited rule cannot be narrowed
     * without changing what other folders load, so it is reported and left unchanged. A marker
     * whose path is not delivered is no violation; the file is delivered as an addition.
     *
     * @param narrowedContents receives the content of each narrowed copy, by sha256
     * @return the violations of the markers
     */
    private static List<Violation> applyOverrides(UUID repositoryId, List<MountCandidate> mounts,
                                                  Map<String, ComposedFile> byPath, Map<String, byte[]> contents,
                                                  Map<String, byte[]> narrowedContents) {
        Map<UUID, MountCandidate> byId = mountsById(mounts);
        List<Violation> violations = new ArrayList<>();
        Map<String, List<ComposedFile>> overridesByTarget = new LinkedHashMap<>();
        for (ComposedFile file : byPath.values()) {
            if (file.overridesPath() == null) {
                continue;
            }
            MountCandidate mount = byId.get(file.ownerId());
            ComposedFile target = byPath.get(file.overridesPath());
            if (mount != null && target == null) {
                continue;
            }
            boolean inherited = target != null && !byId.containsKey(target.ownerId());
            boolean ruleOverride = mount != null && inherited && MountPaths.isMountRule(file.path(), mount.name())
                    && PathRules.isScopableRule(file.path()) && PathRules.isScopableRule(target.path());
            boolean docOverride = mount != null && inherited && MountPaths.isMountDoc(file.path(), mount.name())
                    && MountPaths.isInheritedDoc(target.path());
            if (ruleOverride) {
                overridesByTarget.computeIfAbsent(target.path(), path -> new ArrayList<>()).add(file);
            } else if (!docOverride) {
                violations.add(Violation.ofRepository(repositoryId, file.path(), ViolationRule.OVERRIDE_NOT_ALLOWED,
                        ReleaseConstant.OVERRIDE_NOT_ALLOWED_MESSAGE));
            }
        }
        for (Map.Entry<String, List<ComposedFile>> entry : overridesByTarget.entrySet()) {
            narrowTarget(repositoryId, byPath.get(entry.getKey()), entry.getValue(), byId, byPath, contents, narrowedContents)
                    .forEach(violations::add);
        }
        return violations;
    }

    /**
     * Narrows one inherited rule for every mounted pack that overrides it. Each pattern is expanded
     * and classified against each overriding mount folder; an expansion inside one of them is dropped, and a
     * pattern none of whose expansions is dropped stays as written.
     */
    private static List<Violation> narrowTarget(UUID repositoryId, ComposedFile target, List<ComposedFile> overriding,
                                                Map<UUID, MountCandidate> byId,
                                                Map<String, ComposedFile> byPath, Map<String, byte[]> contents,
                                                Map<String, byte[]> narrowedContents) {
        byte[] content = target.mode() == FileMode.SYMLINK || target.sha256() == null
                ? null
                : contents.get(target.sha256());
        if (content == null) {
            return overriding.stream()
                    .map(file -> Violation.ofRepository(repositoryId, file.path(), ViolationRule.OVERRIDE_NOT_ALLOWED,
                            String.format(ReleaseConstant.OVERRIDE_NOT_NARROWABLE_MESSAGE, target.path())))
                    .toList();
        }
        Frontmatter frontmatter = FrontmatterParser.parse(new String(content, StandardCharsets.UTF_8));
        if (frontmatter.error() != null || !frontmatter.has(ReleaseConstant.FRONTMATTER_PATHS)) {
            return overriding.stream()
                    .map(file -> Violation.ofRepository(repositoryId, file.path(), ViolationRule.RULE_OVERRIDE_ALWAYS_ON,
                            String.format(ReleaseConstant.RULE_OVERRIDE_ALWAYS_ON_MESSAGE, target.path())))
                    .toList();
        }

        List<Violation> violations = new ArrayList<>();
        List<String> patterns = RuleNarrower.patterns(frontmatter);
        List<String> kept = new ArrayList<>();
        for (String pattern : patterns) {
            Map<String, Boolean> dropped = new LinkedHashMap<>();
            for (ComposedFile file : overriding) {
                MountCandidate mount = byId.get(file.ownerId());
                for (Map.Entry<String, PatternClass> expansion
                        : MountPaths.classify(pattern, mount.rootPath()).entrySet()) {
                    if (expansion.getValue() == PatternClass.SPANNING) {
                        violations.add(Violation.ofRepository(repositoryId, file.path(),
                                ViolationRule.RULE_OVERRIDE_SPANS_MOUNT,
                                String.format(ReleaseConstant.RULE_OVERRIDE_SPANS_MOUNT_MESSAGE, expansion.getKey(), target.path(),
                                        mount.rootPath())));
                    }
                    dropped.merge(expansion.getKey(), expansion.getValue() == PatternClass.INSIDE, Boolean::logicalOr);
                }
            }
            if (dropped.containsValue(true)) {
                dropped.entrySet().stream().filter(expansion -> !expansion.getValue()).map(Map.Entry::getKey).forEach(kept::add);
            } else {
                kept.add(pattern);
            }
        }
        if (!violations.isEmpty() || kept.equals(patterns)) {
            return violations;
        }
        if (!RuleNarrower.isNarrowable(content)) {
            return overriding.stream()
                    .map(file -> Violation.ofRepository(repositoryId, file.path(), ViolationRule.OVERRIDE_NOT_ALLOWED,
                            String.format(ReleaseConstant.OVERRIDE_NOT_NARROWABLE_MESSAGE, target.path())))
                    .toList();
        }

        Optional<byte[]> narrowed = RuleNarrower.narrow(content, kept);
        if (narrowed.isEmpty()) {
            byPath.remove(target.path());
            return violations;
        }
        byte[] bytes = narrowed.get();
        String sha256 = sha256Of(bytes);
        narrowedContents.put(sha256, bytes);
        byPath.put(target.path(), new ComposedFile(target.path(), target.mode(), sha256, bytes.length, null, false, target.ownerId(),
                target.versionId(), target.overridesPath(), overriding.get(0).ownerId(), null));
        return violations;
    }

    private static List<String> rulePatterns(byte[] content) {
        if (content == null) {
            return List.of();
        }
        Frontmatter frontmatter = FrontmatterParser.parse(new String(content, StandardCharsets.UTF_8));
        return frontmatter.error() != null ? List.of() : RuleNarrower.patterns(frontmatter);
    }

    private static Map<UUID, MountCandidate> mountsById(List<MountCandidate> mounts) {
        return mounts.stream().collect(Collectors.toMap(MountCandidate::packId, Function.identity()));
    }

    private static String sha256Of(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(ReleaseConstant.SHA256_ALGORITHM).digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            // Every Java platform must provide SHA-256, so this means a broken runtime.
            throw new IllegalStateException(exception);
        }
    }

    /**
     * AGENTS.md and .agents/AGENTS.md always load; a rule loads at every start unless its
     * frontmatter has paths. A rule that is a symlink, or whose frontmatter cannot be read, counts
     * as always-on, so a change to it is never mistaken for one a running session can take.
     */
    private static ComposedFile withAlwaysOn(ComposedFile file, Map<String, byte[]> contents) {
        boolean alwaysOn;
        if (PathRules.isAgentsFile(file.path())) {
            alwaysOn = true;
        } else if (PathRules.isRuleFile(file.path())) {
            alwaysOn = file.mode() == FileMode.SYMLINK
                    || Optional.ofNullable(contents.get(file.sha256()))
                    .map(content -> FrontmatterParser.parse(new String(content, StandardCharsets.UTF_8)))
                    .map(frontmatter -> frontmatter.error() != null || !frontmatter.has(ReleaseConstant.FRONTMATTER_PATHS))
                    .orElse(true);
        } else {
            alwaysOn = false;
        }
        return alwaysOn == file.alwaysOn() ? file : new ComposedFile(file.path(), file.mode(), file.sha256(), file.size(),
                file.symlinkTarget(), alwaysOn, file.ownerId(), file.versionId(), file.overridesPath(), file.narrowedBy(),
                file.deny());
    }

    /**
     * A guardrail carries the deny rules of its frontmatter, which the CLI merges into the delivered
     * settings. A guardrail without deny rules, and every other file, carries none, so its manifest
     * entry is signed as it was before guardrails existed. The rules were checked when the file was
     * saved, so they are taken as written.
     */
    private static ComposedFile withDeny(ComposedFile file, Map<String, byte[]> contents) {
        if (file.symlink() || !PathRules.isGuardrailPath(file.path()) || !file.path().endsWith(ValidationConstant.MARKDOWN_SUFFIX)) {
            return file;
        }
        List<String> deny = Optional.ofNullable(contents.get(file.sha256()))
                .map(content -> FrontmatterParser.parse(new String(content, StandardCharsets.UTF_8)))
                .filter(frontmatter -> frontmatter.error() == null)
                .map(frontmatter -> frontmatter.textList(ReleaseConstant.FRONTMATTER_DENY))
                .filter(rules -> !rules.isEmpty())
                .map(List::copyOf)
                .orElse(null);
        return deny == null ? file : new ComposedFile(file.path(), file.mode(), file.sha256(), file.size(), file.symlinkTarget(),
                file.alwaysOn(), file.ownerId(), file.versionId(), file.overridesPath(), file.narrowedBy(), deny);
    }
}
