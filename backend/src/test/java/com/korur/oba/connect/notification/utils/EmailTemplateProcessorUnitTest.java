package com.korur.oba.connect.notification.utils;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.notification.constant.NotificationConstant;

class EmailTemplateProcessorUnitTest {

    private final TemplateEngine templateEngine = mock(TemplateEngine.class);
    private final EmailResourceManager resourceManager = mock(EmailResourceManager.class);

    @Test
    void process_fixedClock_writesTimestampInUtcWithLabel() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T10:12:00Z"), ZoneOffset.UTC);
        EmailTemplateProcessor processor = new EmailTemplateProcessor(templateEngine, resourceManager, clock);
        when(templateEngine.process(eq("welcome"), org.mockito.ArgumentMatchers.any(IContext.class))).thenReturn("html");

        String result = processor.process("welcome", Map.of("name", "Ada"));

        ArgumentCaptor<IContext> context = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("welcome"), context.capture());
        assertThat(result).isEqualTo("html");
        assertThat(context.getValue().getVariable(NotificationConstant.TIMESTAMP_VARIABLE))
                .isEqualTo("4 November 2026 at 10:12 UTC");
        assertThat(context.getValue().getVariable("name")).isEqualTo("Ada");
    }

    @Test
    void process_instantAfterMidnightInIstanbul_stillWritesUtcDay() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T21:30:00Z"), ZoneOffset.UTC);
        EmailTemplateProcessor processor = new EmailTemplateProcessor(templateEngine, resourceManager, clock);

        processor.process("welcome", null);

        ArgumentCaptor<IContext> context = ArgumentCaptor.forClass(IContext.class);
        verify(templateEngine).process(eq("welcome"), context.capture());
        assertThat(context.getValue().getVariable(NotificationConstant.TIMESTAMP_VARIABLE))
                .isEqualTo("3 November 2026 at 21:30 UTC");
    }
}
