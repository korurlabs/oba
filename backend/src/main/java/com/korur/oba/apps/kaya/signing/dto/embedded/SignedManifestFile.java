package com.korur.oba.apps.kaya.signing.dto.embedded;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * One path of a signed document. sha256 is absent for a SYMLINK and target for any other mode, so
 * the CLI never reads an empty value as a hash or a link. deny is absent on every file but a
 * guardrail with deny rules, so a manifest without guardrails is signed as the same bytes as before
 * guardrails existed.
 *
 * @param path     path relative to the repository root
 * @param sha256   lower-case hex SHA-256 of the content
 * @param mode     FILE, EXECUTABLE or SYMLINK
 * @param target   relative target of a SYMLINK
 * @param alwaysOn whether Claude Code loads the file at every session start
 * @param deny     the deny rules of a guardrail, which the CLI merges into permissions.deny of
 *                 .claude/settings.json; absent otherwise
 */
@JsonPropertyOrder({"path", "sha256", "mode", "target", "alwaysOn", "deny"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignedManifestFile(
        String path,
        String sha256,
        String mode,
        String target,
        boolean alwaysOn,
        List<String> deny
) {
}
