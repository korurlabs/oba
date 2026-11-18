package com.korur.oba.connect.intelligence.core.properties;

import com.korur.oba.connect.intelligence.core.enums.IntelligenceProvider;

// 2. Third-party
import lombok.Getter;
import lombok.Setter;

/**
 * Routing properties for use-case to provider mapping.
 * Maps properties from application.properties with prefix "intelligence.routing".
 */
@Setter
@Getter
public class IntelligenceRoutingProperties {
    private IntelligenceProvider conversation = IntelligenceProvider.GEMINI;
    private IntelligenceProvider scanAnalysis = IntelligenceProvider.DEEPSEEK;
    private IntelligenceProvider threatAnalysis = IntelligenceProvider.MISTRAL;
    private IntelligenceProvider taskGeneration = IntelligenceProvider.ANTHROPIC;
    private IntelligenceProvider ticketAnalysis = IntelligenceProvider.ANTHROPIC;
}
