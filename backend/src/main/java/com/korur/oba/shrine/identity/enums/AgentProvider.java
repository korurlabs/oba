package com.korur.oba.shrine.identity.enums;

/**
 * The vendor behind an agent, fixed when the agent is created. Which flow a provider can serve
 * depends on the connectors the application has, so it is decided when the agent is registered and
 * not stored here.
 */
public enum AgentProvider {

    ANTHROPIC,

    OPENAI,

    XAI,

    DEEPSEEK
}
