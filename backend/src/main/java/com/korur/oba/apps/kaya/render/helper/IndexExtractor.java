package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

// 4. Internal
import com.korur.oba.apps.kaya.agentsfile.dto.embedded.AgentsFileIndex;
import com.korur.oba.apps.kaya.convention.dto.embedded.ConventionIndex;
import com.korur.oba.apps.kaya.guardrail.constant.GuardrailConstant;
import com.korur.oba.apps.kaya.guardrail.dto.embedded.GuardrailIndex;
import com.korur.oba.apps.kaya.guardrail.enums.GuardrailCategory;
import com.korur.oba.apps.kaya.hook.dto.embedded.HookIndex;
import com.korur.oba.apps.kaya.reference.dto.embedded.ReferenceIndex;
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.dto.embedded.SkillPartFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.role.dto.embedded.RoleIndex;
import com.korur.oba.apps.kaya.rule.dto.embedded.RuleIndex;
import com.korur.oba.apps.kaya.script.dto.embedded.ScriptIndex;
import com.korur.oba.apps.kaya.subagent.dto.embedded.SubagentIndex;
import com.korur.oba.apps.kaya.template.dto.embedded.TemplateIndex;
import com.korur.oba.apps.kaya.toolparameter.dto.embedded.ToolParameterIndex;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.validation.helper.PermissionRules;
import com.korur.oba.apps.kaya.workflow.dto.embedded.WorkflowIndex;

/**
 * Extracts the index of each kind from one file. Each method reads the file completely and refuses
 * it before anything is stored, and returns a function that builds the index once the blob id is
 * known, so a parse checks every file first and writes afterwards.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class IndexExtractor {

    private IndexExtractor() {
    }

    /** The index of an AGENTS.md, which has no typed column besides its placement. */
    public static Function<UUID, AgentsFileIndex> agentsFile(DeliveryPath delivery) {
        return blobId -> new AgentsFileIndex(delivery.placement(), blobId);
    }

    /**
     * The index of a rule: its description, its paths and the convention docs its body links to, so
     * the rule's convention relation always matches its Markdown.
     */
    public static Function<UUID, RuleIndex> rule(DeliveryPath delivery, SourceFile file) {
        String path = delivery.path();
        String text = IndexFields.text(path, file.content());
        Frontmatter frontmatter = IndexFields.frontmatter(path, text);
        String description = IndexFields.optionalText(path, frontmatter, RenderConstant.FRONTMATTER_DESCRIPTION,
                RenderConstant.DESCRIPTION_MAX);
        List<String> paths = IndexFields.textList(path, frontmatter, RenderConstant.FRONTMATTER_PATHS);
        List<String> conventionPaths = conventionPaths(path, text);
        return blobId -> new RuleIndex(delivery.relativePath(), blobId, description, paths == null ? List.of() : paths,
                file.overridesPath(), conventionPaths);
    }

    /**
     * The index of a guardrail. The file name must match {@link GuardrailConstant#NAME_PATTERN}; the
     * frontmatter holds only category, description and deny, the category is one of
     * {@link GuardrailCategory}; at most 20 deny rules, each read by {@link PermissionRules} as Claude Code
     * reads it and none twice, because the CLI merges them into permissions.deny as they are; and the
     * instruction text after the frontmatter holds 1 to 4000 characters, not counting surrounding
     * whitespace, since it loads in every session.
     */
    public static Function<UUID, GuardrailIndex> guardrail(DeliveryPath delivery, SourceFile file) {
        String path = delivery.path();
        String name = guardrailName(delivery);
        if (!name.matches(GuardrailConstant.NAME_PATTERN)) {
            throw RenderRefusals.invalidFile(path, RenderConstant.GUARDRAIL_NAME_REASON);
        }
        String text = IndexFields.text(path, file.content());
        Frontmatter frontmatter = IndexFields.frontmatter(path, text);
        for (String key : frontmatter.values().keySet()) {
            if (!RenderConstant.GUARDRAIL_FRONTMATTER_KEYS.contains(key)) {
                throw RenderRefusals.invalidFile(path, String.format(RenderConstant.GUARDRAIL_KEY_UNKNOWN_REASON, key));
            }
        }
        GuardrailCategory category = guardrailCategory(path,
                IndexFields.requiredText(path, frontmatter, RenderConstant.FRONTMATTER_CATEGORY, RenderConstant.NAME_MAX));
        String description = IndexFields.optionalText(path, frontmatter, RenderConstant.FRONTMATTER_DESCRIPTION,
                RenderConstant.DESCRIPTION_MAX);
        List<String> deny = guardrailDeny(path, IndexFields.textList(path, frontmatter, RenderConstant.FRONTMATTER_DENY));
        int bodyLength = IndexFields.body(text, frontmatter).strip().length();
        if (bodyLength == 0) {
            throw RenderRefusals.invalidFile(path, RenderConstant.GUARDRAIL_BODY_EMPTY_REASON);
        }
        if (bodyLength > GuardrailConstant.BODY_MAX_CHARACTERS) {
            throw RenderRefusals.invalidFile(path, String.format(RenderConstant.GUARDRAIL_BODY_TOO_LONG_REASON, bodyLength,
                    GuardrailConstant.BODY_MAX_CHARACTERS));
        }
        return blobId -> new GuardrailIndex(name, blobId, category, description, deny);
    }

    /**
     * The name of a guardrail: its path below .agents/rules/guardrails/ without .md. A removal is
     * classified only, so its name may be one no stored guardrail has.
     *
     * @param delivery a {@link com.korur.oba.apps.kaya.render.enums.FileKind#GUARDRAIL} path
     * @return the name
     */
    public static String guardrailName(DeliveryPath delivery) {
        String relativePath = delivery.relativePath();
        return relativePath.substring(0, relativePath.length() - RenderConstant.MARKDOWN_SUFFIX.length());
    }

    private static GuardrailCategory guardrailCategory(String path, String value) {
        try {
            return GuardrailCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            // An unknown category is the file's fault: it is refused with the categories it may name.
            throw RenderRefusals.invalidFile(path, String.format(RenderConstant.GUARDRAIL_CATEGORY_UNKNOWN_REASON, value,
                    Arrays.toString(GuardrailCategory.values())));
        }
    }

    private static List<String> guardrailDeny(String path, List<String> deny) {
        if (deny == null) {
            return List.of();
        }
        if (deny.size() > GuardrailConstant.DENY_MAX) {
            throw RenderRefusals.invalidFile(path, String.format(RenderConstant.GUARDRAIL_DENY_TOO_MANY_REASON, deny.size(),
                    GuardrailConstant.DENY_MAX));
        }
        if (deny.stream().anyMatch(rule -> rule.length() > GuardrailConstant.DENY_RULE_MAX_LENGTH)) {
            throw RenderRefusals.invalidFile(path, String.format(RenderConstant.GUARDRAIL_DENY_TOO_LONG_REASON,
                    GuardrailConstant.DENY_RULE_MAX_LENGTH));
        }
        List<Map<String, Object>> problems = PermissionRules.findProblems(deny, null, null);
        if (!problems.isEmpty()) {
            Map<String, Object> first = problems.getFirst();
            throw RenderRefusals.invalidFile(path, String.format(RenderConstant.GUARDRAIL_DENY_REFUSED_REASON,
                    first.get(ValidationConstant.FIELD_RULE), first.get(ValidationConstant.FIELD_PROBLEM)));
        }
        return List.copyOf(deny);
    }

    /**
     * The convention docs a Markdown text links to, each once.
     *
     * @param path the delivered path of the text, which relative links resolve against
     * @param text the text
     * @return the repository-relative paths below docs/private/conventions/, in order of appearance
     */
    public static List<String> conventionPaths(String path, String text) {
        return LinkExtractor.extract(path, text).stream()
                .map(LinkCandidate::targetPath)
                .filter(target -> target.startsWith(RenderConstant.CONVENTIONS_FOLDER))
                .filter(target -> target.length() <= RenderConstant.RELATIVE_PATH_MAX)
                .distinct()
                .toList();
    }

    /** The index of a delivered convention doc. */
    public static Function<UUID, ConventionIndex> convention(DeliveryPath delivery, SourceFile file) {
        String title = title(delivery.path(), file.content());
        return blobId -> new ConventionIndex(delivery.relativePath(), blobId, title, file.overridesPath());
    }

    /** The index of a role. */
    public static Function<UUID, RoleIndex> role(DeliveryPath delivery, SourceFile file) {
        String title = title(delivery.path(), file.content());
        return blobId -> new RoleIndex(delivery.relativePath(), blobId, title);
    }

    /** The index of a delivered workflow. */
    public static Function<UUID, WorkflowIndex> workflow(DeliveryPath delivery, SourceFile file) {
        String title = title(delivery.path(), file.content());
        return blobId -> new WorkflowIndex(delivery.relativePath(), blobId, title);
    }

    /** The index of a template. */
    public static Function<UUID, TemplateIndex> template(DeliveryPath delivery, SourceFile file) {
        String title = title(delivery.path(), file.content());
        return blobId -> new TemplateIndex(delivery.relativePath(), blobId, title);
    }

    /** The index of a hook script; only the executable bit is indexed. */
    public static Function<UUID, HookIndex> hook(DeliveryPath delivery, SourceFile file) {
        boolean executable = file.mode() == FileMode.EXECUTABLE;
        return blobId -> new HookIndex(delivery.relativePath(), blobId, executable);
    }

    /**
     * The index of a subagent. The name is required and maxTurns must be 1 or more, as the table
     * requires; the uniqueness of the name across the version is the caller's check.
     */
    public static Function<UUID, SubagentIndex> subagent(DeliveryPath delivery, SourceFile file) {
        String path = delivery.path();
        Frontmatter frontmatter = IndexFields.frontmatter(path, IndexFields.text(path, file.content()));
        String name = IndexFields.requiredText(path, frontmatter, RenderConstant.FRONTMATTER_NAME, RenderConstant.NAME_MAX);
        String description = IndexFields.optionalText(path, frontmatter, RenderConstant.FRONTMATTER_DESCRIPTION,
                RenderConstant.DESCRIPTION_MAX);
        String model = IndexFields.optionalText(path, frontmatter, RenderConstant.FRONTMATTER_MODEL, RenderConstant.MODEL_MAX);
        Integer maxTurns = IndexFields.optionalInteger(path, frontmatter, RenderConstant.FRONTMATTER_MAX_TURNS);
        if (maxTurns != null && maxTurns < 1) {
            throw RenderRefusals.invalidFile(path, RenderConstant.MAX_TURNS_REASON);
        }
        List<String> tools = IndexFields.toolList(path, frontmatter, RenderConstant.FRONTMATTER_TOOLS);
        return blobId -> new SubagentIndex(delivery.relativePath(), blobId, name, description, model, maxTurns, tools);
    }

    /** The index of a workflow part, which no folder delivers. */
    public static WorkflowIndex workflowPart(SkillPartFile part, UUID blobId) {
        return new WorkflowIndex(null, blobId, part.title());
    }

    /** The index of a convention part, which no folder delivers. */
    public static ConventionIndex conventionPart(SkillPartFile part, UUID blobId) {
        return new ConventionIndex(null, blobId, part.title(), null);
    }

    /** The index of a script part. */
    public static ScriptIndex scriptPart(SkillPartFile part, UUID blobId) {
        return new ScriptIndex(part.name(), blobId, part.executable());
    }

    /** The index of a reference part. */
    public static ReferenceIndex referencePart(SkillPartFile part, UUID blobId) {
        return new ReferenceIndex(part.name(), blobId, part.mediaType());
    }

    /** The index of a tool parameter part. */
    public static ToolParameterIndex toolParameterPart(SkillPartFile part, UUID blobId) {
        return new ToolParameterIndex(part.name(), blobId, part.format());
    }

    private static String title(String path, byte[] content) {
        String text = IndexFields.text(path, content);
        return IndexFields.title(path, IndexFields.frontmatter(path, text), text);
    }
}
