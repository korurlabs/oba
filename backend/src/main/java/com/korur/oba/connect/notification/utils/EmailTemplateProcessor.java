package com.korur.oba.connect.notification.utils;

// 1. Framework-level
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.notification.constant.NotificationConstant;

@Component
@RequiredArgsConstructor
public class EmailTemplateProcessor {

    /**
     * Formats the email timestamp in UTC with its label, never in the host zone,
     * so a laptop and a pod write the same text for the same moment.
     */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern(NotificationConstant.TIMESTAMP_PATTERN, Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private final TemplateEngine templateEngine;
    private final EmailResourceManager resourceManager;
    private final Clock clock;

    public String process(String templateName, Map<String, String> variables) {
        Map<String, Object> enrichedVariables = enrichVariables(variables);
        Context context = new Context();
        context.setVariables(enrichedVariables);
        return templateEngine.process(templateName, context);
    }

    private Map<String, Object> enrichVariables(Map<String, String> userVariables) {
        Map<String, Object> enriched = new HashMap<>();

        if (userVariables != null) {
            enriched.putAll(userVariables);
        }

        enriched.put(NotificationConstant.SENSEY_LOGO_VARIABLE, resourceManager.getLogo());
        enriched.put(NotificationConstant.COMMON_CSS_VARIABLE, resourceManager.getCss());
        enriched.put(NotificationConstant.TIMESTAMP_VARIABLE, TIMESTAMP_FORMATTER.format(clock.instant()));

        return enriched;
    }
}
