package com.korur.oba.apps.kaya.validation.dto.embedded;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.validation.enums.ViolationRule;

/**
 * One broken {@link ViolationRule}. It travels in the errorValues of a 422 KAY_CFG_001 or a 409
 * KAY_CFG_003 under the key "violations", so the editor can mark each path; only the fields that
 * apply are set.
 */
@Schema(description = "A validation rule a file, a repository's composition or a pack list breaks")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Violation(
        @Schema(description = "Path the rule concerns; null for a rule about a pack list", nullable = true,
                example = ".agents/skills/kaizen/SKILL.md")
        String path,

        @Schema(description = "The rule", example = "SKILL_TOO_LONG")
        ViolationRule rule,

        @Schema(description = "What is wrong, in words", example = "SKILL.md has 1512 lines; it must stay under 1500")
        String message,

        @Schema(description = "Repository whose composition breaks the rule", nullable = true,
                example = "3c4d5e6f-7a8b-4c9d-0e1f-2a3b4c5d6e7f")
        UUID repositoryId,

        @Schema(description = "Packs or projects involved: both owners of PATH_IN_TWO_SETS, the pack of NOT_A_PACK or "
                + "PACK_DUPLICATE", nullable = true)
        List<UUID> ownerIds,

        @Schema(description = "Mount folder the rule must be scoped to (MOUNT_RULE_SCOPE only)", nullable = true,
                example = "projects/kagi")
        String rootPath
) {

    /**
     * Every field but the mount folder, which only MOUNT_RULE_SCOPE sets.
     *
     * @param path         the path the rule concerns
     * @param rule         the rule
     * @param message      what is wrong
     * @param repositoryId the repository whose composition breaks it
     * @param ownerIds     the packs involved
     */
    public Violation(String path, ViolationRule rule, String message, UUID repositoryId, List<UUID> ownerIds) {
        this(path, rule, message, repositoryId, ownerIds, null);
    }

    /**
     * A rule of a mounted pack that is not scoped to its mount folder.
     *
     * @param repositoryId the repository whose composition delivers it; null for a check of the pack alone
     * @param path         the rule
     * @param packId       the mounted pack
     * @param rootPath     the mount folder
     * @param message      what is wrong
     * @return the violation
     */
    public static Violation ofMountRule(UUID repositoryId, String path, UUID packId, String rootPath, String message) {
        return new Violation(path, ViolationRule.MOUNT_RULE_SCOPE, message, repositoryId, List.of(packId), rootPath);
    }

    /**
     * A rule one path breaks on its own.
     *
     * @param path    the path
     * @param rule    the rule
     * @param message what is wrong
     * @return the violation
     */
    public static Violation ofPath(String path, ViolationRule rule, String message) {
        return new Violation(path, rule, message, null, null);
    }

    /**
     * A rule a path breaks within one repository's composition.
     *
     * @param repositoryId the repository
     * @param path         the path
     * @param rule         the rule
     * @param message      what is wrong
     * @return the violation
     */
    public static Violation ofRepository(UUID repositoryId, String path, ViolationRule rule, String message) {
        return new Violation(path, rule, message, repositoryId, null);
    }

    /**
     * A rule one pack or project breaks, such as a pack listed twice.
     *
     * @param ownerId the pack or project
     * @param rule    the rule
     * @param message what is wrong
     * @return the violation
     */
    public static Violation ofOwner(UUID ownerId, ViolationRule rule, String message) {
        return new Violation(null, rule, message, null, List.of(ownerId));
    }
}
