package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

// 3. Java SDK
import java.util.LinkedHashMap;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.render.constant.RenderConstant;

/**
 * Reads the frontmatter block of a Markdown file the way Claude Code does: a first line "---",
 * YAML, and a closing "---" line. SnakeYAML runs with its safe constructor, so a file cannot make
 * the parser build Java objects, and with its default alias limit, so a file cannot expand without
 * bound.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class FrontmatterParser {

    private static final String DELIMITER = "---";

    private FrontmatterParser() {
    }

    /**
     * Parses the frontmatter of a text.
     *
     * @param text the file content
     * @return the block's keys, absent when the text has no block, or the reason it is unreadable
     */
    public static Frontmatter parse(String text) {
        String normalised = text.startsWith("\uFEFF") ? text.substring(1) : text;
        String[] lines = normalised.split("\r?\n", -1);
        if (lines.length == 0 || !lines[0].strip().equals(DELIMITER)) {
            return new Frontmatter(false, Map.of(), null);
        }
        StringBuilder block = new StringBuilder();
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].strip().equals(DELIMITER)) {
                return load(block.toString());
            }
            block.append(lines[index]).append('\n');
        }
        return new Frontmatter(true, Map.of(), RenderConstant.FRONTMATTER_NOT_CLOSED_MESSAGE);
    }

    private static Frontmatter load(String block) {
        try {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(block);
            if (loaded == null) {
                return new Frontmatter(true, Map.of(), null);
            }
            if (!(loaded instanceof Map<?, ?> map)) {
                return new Frontmatter(true, Map.of(), RenderConstant.FRONTMATTER_NOT_MAPPING_MESSAGE);
            }
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
            return new Frontmatter(true, values, null);
        } catch (YAMLException e) {
            // The parse error is the result: the caller reports it as a violation of the file.
            return new Frontmatter(true, Map.of(), e.getMessage());
        }
    }
}
