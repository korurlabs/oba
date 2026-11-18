package com.korur.oba.connect.outbox.annotation;

// 3. Java SDK
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 4. Internal
import com.korur.oba.connect.outbox.enums.ConnectOutboxPolicy;
import com.korur.oba.connect.outbox.enums.OutboxEventType;

/**
 * Marks a method for fire-and-forget execution via the transactional outbox.
 *
 * <p>When intercepted by {@link com.korur.oba.connect.outbox.aspect.ConnectOutboxAspect},
 * the method's arguments are serialized as a JSON payload and enqueued as an
 * {@code OutboxEvent}. The method body is NOT executed immediately — the scheduler
 * picks up the event later and dispatches it to the matching handler.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * {@literal @}ConnectOutbox(value = ConnectOutboxPolicy.AZURE_BLOB_STORAGE,
 *                eventType = OutboxEventType.AZURE_BLOB_DELETE)
 * public void deleteFileAsync(String fileUrl) {
 *     // body not executed by AOP — handler calls the underlying service
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConnectOutbox {

    /**
     * The outbox policy to apply, defining the target system and retry schedule.
     */
    ConnectOutboxPolicy value();

    /**
     * The event type discriminator for handler routing.
     */
    OutboxEventType eventType();
}
