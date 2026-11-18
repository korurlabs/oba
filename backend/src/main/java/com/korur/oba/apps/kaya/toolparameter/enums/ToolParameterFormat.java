package com.korur.oba.apps.kaya.toolparameter.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The syntax of a tool parameter file, taken from its extension on parse so the editor can highlight and
 * check it.
 */
public enum ToolParameterFormat {
    // A .json file.
    JSON,
    // A .yaml or .yml file.
    YAML,
    // Any other extension; the content is shown as plain text.
    TEXT
}
