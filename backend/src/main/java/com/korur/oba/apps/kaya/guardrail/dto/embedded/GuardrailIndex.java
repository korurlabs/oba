package com.korur.oba.apps.kaya.guardrail.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.guardrail.enums.GuardrailCategory;

/**
 * The index of one guardrail file as the render domain extracts it on parse: the stored blob and the
 * frontmatter fields. The enforcement is not part of it, because it follows from the deny rules.
 *
 * @param name          the file name below .agents/rules/guardrails/ without .md
 * @param contentBlobId the blob holding the whole file, frontmatter included
 * @param category      the frontmatter category
 * @param description   the frontmatter description, or null
 * @param deny          the frontmatter deny rules; empty for an instruction only
 */
public record GuardrailIndex(
        String name,
        UUID contentBlobId,
        GuardrailCategory category,
        String description,
        List<String> deny
) {
}
