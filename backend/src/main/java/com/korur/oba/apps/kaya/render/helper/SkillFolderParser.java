package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

// 3. Java SDK
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

// 4. Internal
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.dto.embedded.SkillFolder;
import com.korur.oba.apps.kaya.render.dto.embedded.SkillPartFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;
import com.korur.oba.apps.kaya.toolparameter.enums.ToolParameterFormat;

/**
 * Maps the files of one skill directory to the skill and its parts: SKILL.md to the
 * {@link com.korur.oba.apps.kaya.skill.entity.Skill}; scripts/** and any file with the executable bit
 * to a {@link SkillPartKind#SCRIPT}; workflows/** to a {@link SkillPartKind#WORKFLOW}; rules/** and
 * conventions/** to a {@link SkillPartKind#CONVENTION}; other .json, .yaml and .yml files, such as
 * agents/openai.yml or evals/evals.json, to a {@link SkillPartKind#TOOL_PARAMETER}; everything else,
 * such as references/**, assets/** and loose Markdown, to a {@link SkillPartKind#REFERENCE}.
 *
 * The executable bit is read first, because only a script keeps it: an executable file mapped to any
 * other kind would render without it. Each file keeps its path as the link path, so the links inside
 * SKILL.md still resolve.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SkillFolderParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private SkillFolderParser() {
    }

    /**
     * Parses one skill directory.
     *
     * @param name  the skill name, the directory name
     * @param files every file below .agents/skills/name/, each with content and no symlink
     * @return the skill with its parts sorted by path
     */
    public static SkillFolder parse(String name, List<SourceFile> files) {
        String directory = DeliveryPaths.skillDirectory(name);
        if (name.length() > RenderConstant.NAME_MAX) {
            throw RenderRefusals.invalidFile(directory,
                    String.format(RenderConstant.FIELD_TOO_LONG_REASON, RenderConstant.FRONTMATTER_NAME, RenderConstant.NAME_MAX));
        }
        byte[] skillFile = null;
        List<SkillPartFile> parts = new ArrayList<>();
        for (SourceFile file : files) {
            String path = file.path().substring(directory.length());
            if (path.equals(RenderConstant.SKILL_FILE)) {
                skillFile = file.content();
            } else {
                parts.add(part(file, path));
            }
        }
        if (skillFile == null) {
            throw RenderRefusals.invalidFile(directory, String.format(RenderConstant.SKILL_WITHOUT_SKILL_FILE_REASON, directory));
        }
        String skillPath = directory + RenderConstant.SKILL_FILE;
        Frontmatter frontmatter = IndexFields.frontmatter(skillPath, IndexFields.text(skillPath, skillFile));
        String description = IndexFields.optionalText(skillPath, frontmatter, RenderConstant.FRONTMATTER_DESCRIPTION,
                RenderConstant.DESCRIPTION_MAX);
        parts.sort(Comparator.comparing(SkillPartFile::path));
        return new SkillFolder(name, skillFile, description, parts);
    }

    /**
     * The part kind of a file inside a skill directory.
     *
     * @param path the path inside the skill directory
     * @param mode the mode of the file
     * @return the kind that stores it
     */
    public static SkillPartKind kindOf(String path, FileMode mode) {
        if (mode == FileMode.EXECUTABLE || path.startsWith(RenderConstant.SKILL_SCRIPTS_FOLDER)) {
            return SkillPartKind.SCRIPT;
        }
        if (path.startsWith(RenderConstant.SKILL_WORKFLOWS_FOLDER)) {
            return SkillPartKind.WORKFLOW;
        }
        if (path.startsWith(RenderConstant.SKILL_RULES_FOLDER) || path.startsWith(RenderConstant.SKILL_CONVENTIONS_FOLDER)) {
            return SkillPartKind.CONVENTION;
        }
        return formatOf(path) == null ? SkillPartKind.REFERENCE : SkillPartKind.TOOL_PARAMETER;
    }

    private static SkillPartFile part(SourceFile file, String path) {
        if (path.length() > RenderConstant.SKILL_PART_PATH_MAX) {
            throw RenderRefusals.invalidFile(file.path(), String.format(RenderConstant.PATH_TOO_LONG_REASON, RenderConstant.SKILL_PART_PATH_MAX));
        }
        String name = path.substring(path.lastIndexOf(RenderConstant.PATH_SEPARATOR) + 1);
        SkillPartKind kind = kindOf(path, file.mode());
        byte[] content = file.content();
        return switch (kind) {
            case SCRIPT -> new SkillPartFile(kind, path, name, content, file.mode() == FileMode.EXECUTABLE, null, null, null);
            case WORKFLOW, CONVENTION -> new SkillPartFile(kind, path, name, content, false, titleOf(file.path(), name, content), null, null);
            case TOOL_PARAMETER -> new SkillPartFile(kind, path, name, content, false, null, null, checkedFormat(file.path(), path, content));
            case REFERENCE -> new SkillPartFile(kind, path, name, content, false, null, mediaTypeOf(name), null);
        };
    }

    // Only a Markdown part has a title; a workflow or convention in another format is stored without.
    private static String titleOf(String deliveredPath, String name, byte[] content) {
        if (!name.endsWith(RenderConstant.MARKDOWN_SUFFIX)) {
            return null;
        }
        String text = IndexFields.text(deliveredPath, content);
        return IndexFields.title(deliveredPath, IndexFields.frontmatter(deliveredPath, text), text);
    }

    private static ToolParameterFormat formatOf(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (RenderConstant.TOOL_PARAMETER_JSON_SUFFIXES.stream().anyMatch(lower::endsWith)) {
            return ToolParameterFormat.JSON;
        }
        if (RenderConstant.TOOL_PARAMETER_YAML_SUFFIXES.stream().anyMatch(lower::endsWith)) {
            return ToolParameterFormat.YAML;
        }
        return null;
    }

    // The file is read once so a tool parameter file that a tool could not load is refused at save.
    private static ToolParameterFormat checkedFormat(String deliveredPath, String path, byte[] content) {
        ToolParameterFormat format = formatOf(path);
        String text = IndexFields.text(deliveredPath, content);
        if (format == ToolParameterFormat.JSON) {
            try {
                JSON.readTree(text);
            } catch (JsonProcessingException e) {
                throw RenderRefusals.invalidFile(deliveredPath,
                        String.format(RenderConstant.TOOL_PARAMETER_INVALID_REASON, format.name(), e.getOriginalMessage()));
            }
        } else {
            try {
                // Each document is loaded in turn, because loadAll reads lazily.
                Iterator<Object> documents = new Yaml(new SafeConstructor(new LoaderOptions())).loadAll(text).iterator();
                while (documents.hasNext()) {
                    documents.next();
                }
            } catch (YAMLException e) {
                throw RenderRefusals.invalidFile(deliveredPath,
                        String.format(RenderConstant.TOOL_PARAMETER_INVALID_REASON, format.name(), e.getMessage()));
            }
        }
        return format;
    }

    private static String mediaTypeOf(String name) {
        if (name.toLowerCase(Locale.ROOT).endsWith(RenderConstant.MARKDOWN_SUFFIX)) {
            return RenderConstant.MEDIA_TYPE_MARKDOWN;
        }
        String guessed = URLConnection.guessContentTypeFromName(name);
        return guessed == null ? RenderConstant.MEDIA_TYPE_FALLBACK : guessed;
    }
}
