package com.korur.oba.shrine.agent.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 3. Java SDK
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// 4. Internal
import com.korur.oba.connect.routine.core.enums.RoutineProvider;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;

/**
 * Decides which providers each agent flow accepts, from the connectors the application has rather than
 * from a stored table. A ROUTINE agent is fired through the routine connector, so it needs a provider
 * that connector serves; an API agent is run by Oba through a model connector, which every provider has.
 */
@Component
public class AgentFlowHelper {

    /**
     * The routine vendor of an agent provider. A provider that hosts no routine today maps to none;
     * the switch has no default, so a new provider does not compile until it is decided here.
     *
     * @param provider the agent provider
     * @return the routine provider, or empty when the vendor has no routine connector
     */
    public Optional<RoutineProvider> routineProviderOf(AgentProvider provider) {
        return switch (provider) {
            case ANTHROPIC -> Optional.of(RoutineProvider.ANTHROPIC);
            case OPENAI, XAI, DEEPSEEK -> Optional.empty();
        };
    }

    /**
     * @param flow the agent flow
     * @return the providers the flow accepts, in declaration order
     */
    public List<AgentProvider> providersOf(AgentFlow flow) {
        return switch (flow) {
            case ROUTINE -> {
                Set<RoutineProvider> routineProviders = EnumSet.allOf(RoutineProvider.class);
                yield Arrays.stream(AgentProvider.values())
                        .filter(provider -> routineProviderOf(provider).filter(routineProviders::contains).isPresent())
                        .toList();
            }
            case API -> List.of(AgentProvider.values());
        };
    }

    /**
     * @param flow     the agent flow
     * @param provider the agent provider
     * @return true when an agent of the flow can be registered with the provider
     */
    public boolean accepts(AgentFlow flow, AgentProvider provider) {
        return providersOf(flow).contains(provider);
    }
}
