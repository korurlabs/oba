package com.korur.oba.dojo.logging.provider;

// 1. Framework-level
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractJsonProvider;

// 3. Java SDK
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Emits a monotonically increasing counter for each log event.
 *
 * <p>Starts at 0 on application boot and increments by 1 per log line.
 * Useful for detecting gaps in log pipelines or ordering events
 * that share the same millisecond timestamp.</p>
 *
 * <p>Configured in logback-spring.xml as a LogstashEncoder provider:</p>
 * <pre>{@code
 * <provider class="com.korur.oba.dojo.logging.provider.LogCounterJsonProvider">
 *     <fieldName>logCounter</fieldName>
 * </provider>
 * }</pre>
 */
public class LogCounterJsonProvider extends AbstractJsonProvider<ILoggingEvent> {

    private static final AtomicLong COUNTER = new AtomicLong(0);

    private String fieldName = "logCounter";

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        generator.writeNumberField(fieldName, COUNTER.getAndIncrement());
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
}
