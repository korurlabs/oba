package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// 4. Internal

/**
 * The YAML frontmatter of a Markdown file.
 *
 * @param present whether the file starts with a frontmatter block
 * @param values  its top-level keys; empty when absent or invalid
 * @param error   why the block cannot be read; null when it can
 */
public record Frontmatter(boolean present, Map<String, Object> values, String error) {

    /** Whether the block has the key, even with an empty value. */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** A scalar value as text; null when the key is absent or not a scalar. */
    public String text(String key) {
        Object value = values.get(key);
        return value instanceof Collection<?> || value instanceof Map<?, ?> || value == null ? null : value.toString();
    }

    /** A value that is one string or a list of strings, such as paths; null when absent. */
    public List<String> textList(String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        List<String> items = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> items.add(String.valueOf(item)));
        } else {
            items.add(value.toString());
        }
        return items;
    }
}
