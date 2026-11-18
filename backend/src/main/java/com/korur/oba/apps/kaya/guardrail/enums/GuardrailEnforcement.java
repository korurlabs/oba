package com.korur.oba.apps.kaya.guardrail.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * How a guardrail takes effect, derived from its deny rules and never stored.
 */
public enum GuardrailEnforcement {
    // The guardrail has deny rules, which Claude Code applies to every tool call whatever the prompt says.
    BLOCK,
    // The guardrail has no deny rules, so it acts only as an always-on instruction.
    INSTRUCT
}
