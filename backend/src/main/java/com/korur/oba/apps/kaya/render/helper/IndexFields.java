package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.render.constant.RenderConstant;

/**
 * Reads the typed index columns of a Markdown kind from its text and {@link Frontmatter}. Each reader
 * refuses a value the column cannot hold with {@link RenderRefusals#invalidFile}, so a file whose
 * index cannot be stored is never stored at all. The file itself stays untouched: these values are an
 * index, never rendered back.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class IndexFields {

    private static final String LINE_BREAK = "\r?\n";
    private static final String LIST_SEPARATOR = ",";
    private static final String FRONTMATTER_DELIMITER = "---";

    private IndexFields() {
    }

    /**
     * Decodes a Markdown file, refusing bytes that are not UTF-8 rather than replacing them.
     *
     * @param path    the delivered path, for the refusal
     * @param content the bytes
     * @return the text
     */
    public static String text(String path, byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw RenderRefusals.invalidFile(path, RenderConstant.NOT_UTF8_REASON);
        }
    }

    /**
     * Parses the frontmatter of a Markdown text, refusing one that cannot be read.
     *
     * @param path the delivered path, for the refusal
     * @param text the file text
     * @return the frontmatter, not present when the text has none
     */
    public static Frontmatter frontmatter(String path, String text) {
        Frontmatter frontmatter = FrontmatterParser.parse(text);
        if (frontmatter.error() != null) {
            throw RenderRefusals.invalidFile(path, String.format(RenderConstant.FRONTMATTER_INVALID_REASON, frontmatter.error()));
        }
        return frontmatter;
    }

    /**
     * A scalar field as text.
     *
     * @param path        the delivered path, for the refusal
     * @param frontmatter the frontmatter
     * @param key         the key
     * @param maxLength   the column length
     * @return the text; null when the key is absent or empty
     */
    public static String optionalText(String path, Frontmatter frontmatter, String key, int maxLength) {
        Object value = frontmatter.values().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
            throw RenderRefusals.invalidFile(path, String.format(RenderConstant.FIELD_NOT_TEXT_REASON, key));
        }
        return withinLength(path, key, value.toString(), maxLength);
    }

    /**
     * A scalar field that must be there.
     *
     * @param path        the delivered path, for the refusal
     * @param frontmatter the frontmatter
     * @param key         the key
     * @param maxLength   the column length
     * @return the text, never blank
     */
    public static String requiredText(String path, Frontmatter frontmatter, String key, int maxLength) {
        String value = optionalText(path, frontmatter, key, maxLength);
        if (value == null || value.isBlank()) {
            throw RenderRefusals.invalidFile(path, String.format(RenderConstant.FIELD_REQUIRED_REASON, key));
        }
        return value;
    }

    /**
     * A field that is one text or a list of texts, such as the paths of a rule.
     *
     * @param path        the delivered path, for the refusal
     * @param frontmatter the frontmatter
     * @param key         the key
     * @return the items; null when the key is absent or empty
     */
    public static List<String> textList(String path, Frontmatter frontmatter, String key) {
        Object value = frontmatter.values().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?>) {
            throw RenderRefusals.invalidFile(path, String.format(RenderConstant.FIELD_NOT_TEXT_LIST_REASON, key));
        }
        List<String> items = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item == null || item instanceof Collection<?> || item instanceof Map<?, ?>) {
                    throw RenderRefusals.invalidFile(path, String.format(RenderConstant.FIELD_NOT_TEXT_LIST_REASON, key));
                }
                items.add(item.toString());
            }
        } else {
            items.add(value.toString());
        }
        return items;
    }

    /**
     * A tool list, written by Claude Code as a list or as one comma-separated text such as
     * "Read, Grep, Glob".
     *
     * @param path        the delivered path, for the refusal
     * @param frontmatter the frontmatter
     * @param key         the key
     * @return the tool names, trimmed; null when the key is absent or empty
     */
    public static List<String> toolList(String path, Frontmatter frontmatter, String key) {
        List<String> items = textList(path, frontmatter, key);
        if (items == null) {
            return null;
        }
        List<String> tools = new ArrayList<>();
        for (String item : items) {
            for (String tool : item.split(LIST_SEPARATOR)) {
                if (!tool.isBlank()) {
                    tools.add(tool.strip());
                }
            }
        }
        return tools;
    }

    /**
     * A whole-number field.
     *
     * @param path        the delivered path, for the refusal
     * @param frontmatter the frontmatter
     * @param key         the key
     * @return the number; null when the key is absent or empty
     */
    public static Integer optionalInteger(String path, Frontmatter frontmatter, String key) {
        Object value = frontmatter.values().get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Integer number)) {
            throw RenderRefusals.invalidFile(path, String.format(RenderConstant.FIELD_NOT_INTEGER_REASON, key));
        }
        return number;
    }

    /**
     * The title of a role, template, workflow or convention: the frontmatter title, else the text of
     * the first level-one heading when it fits the column.
     *
     * @param path        the delivered path, for the refusal
     * @param frontmatter the frontmatter
     * @param text        the file text
     * @return the title; null when there is none
     */
    public static String title(String path, Frontmatter frontmatter, String text) {
        String title = optionalText(path, frontmatter, RenderConstant.FRONTMATTER_TITLE, RenderConstant.TITLE_MAX);
        if (title != null) {
            return title;
        }
        String[] lines = text.split(LINE_BREAK);
        // The frontmatter block is skipped, because a YAML comment there also starts with "# ".
        int start = 0;
        if (frontmatter.present()) {
            start = 1;
            while (start < lines.length && !lines[start].strip().equals(FRONTMATTER_DELIMITER)) {
                start++;
            }
            start++;
        }
        for (int index = start; index < lines.length; index++) {
            String line = lines[index];
            if (line.startsWith(RenderConstant.MARKDOWN_HEADING_PREFIX)) {
                String heading = line.substring(RenderConstant.MARKDOWN_HEADING_PREFIX.length()).strip();
                return heading.isEmpty() || heading.length() > RenderConstant.TITLE_MAX ? null : heading;
            }
        }
        return null;
    }

    /**
     * The text after the frontmatter block, as Claude Code loads it as instructions.
     *
     * @param text        the file text
     * @param frontmatter its frontmatter, already read without error
     * @return the text after the line that closes the block; the whole text when it has no block
     */
    public static String body(String text, Frontmatter frontmatter) {
        if (!frontmatter.present()) {
            return text;
        }
        // Lines are read as FrontmatterParser reads them, so the block ends at the same line.
        int position = text.indexOf('\n');
        while (position >= 0 && position < text.length()) {
            int start = position + 1;
            int end = text.indexOf('\n', start);
            String line = end < 0 ? text.substring(start) : text.substring(start, end);
            if (line.strip().equals(FRONTMATTER_DELIMITER)) {
                return end < 0 ? "" : text.substring(end + 1);
            }
            position = end;
        }
        return "";
    }

    private static String withinLength(String path, String key, String value, int maxLength) {
        if (value.length() > maxLength) {
            throw RenderRefusals.invalidFile(path, String.format(RenderConstant.FIELD_TOO_LONG_REASON, key, maxLength));
        }
        return value;
    }
}
