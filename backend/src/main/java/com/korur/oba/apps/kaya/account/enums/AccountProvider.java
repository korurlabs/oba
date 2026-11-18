package com.korur.oba.apps.kaya.account.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The AI product an account belongs to, named after the product rather than the company.
 *
 * Only {@link #CLAUDE} accounts are written today: the recorder reads Claude Code's own account
 * file and nothing else.
 */
public enum AccountProvider {
    CLAUDE,
    CHATGPT,
    GEMINI,
    GROK,
    DEEPSEEK
}
