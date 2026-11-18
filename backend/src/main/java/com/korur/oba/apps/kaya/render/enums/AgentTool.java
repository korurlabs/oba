package com.korur.oba.apps.kaya.render.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * An AI coding tool whose reads the tool view of the path mapping page describes, in the order the
 * page lists them. What each tool reads is in {@link com.korur.oba.apps.kaya.render.helper.ToolPaths}.
 *
 * - CLAUDE_CODE: Claude Code, Anthropic's CLI and IDE agent.
 * - CODEX: OpenAI Codex, the agent in ChatGPT and the Codex CLI.
 * - CURSOR: the Cursor editor and its agent.
 * - GEMINI_CLI: Google's Gemini CLI.
 */
public enum AgentTool {
    CLAUDE_CODE,
    CODEX,
    CURSOR,
    GEMINI_CLI
}
