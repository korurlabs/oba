package com.korur.oba.apps.kaya.render.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * How the content of a file response is encoded. Delivered files are mostly text, so UTF_8 is the
 * normal case; content that is not valid UTF-8 (a binary skill asset) is sent as BASE64 so no byte is
 * lost.
 */
public enum FileEncoding {
    UTF_8,
    BASE64
}
