package com.korur.oba.apps.kaya.guardrail.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * What a guardrail protects, as its frontmatter category names it; the Guardrails page groups the
 * guardrails of a scope by it, in this order.
 */
public enum GuardrailCategory {
    // Secrets, credentials and destructive commands.
    SECURITY,
    // The language agents answer and write in.
    LANGUAGE,
    // Code and review quality.
    QUALITY,
    // Handling of personal or customer data.
    DATA,
    OTHER
}
