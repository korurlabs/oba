package com.korur.oba.connect.retry.annotation;

// 3. Java SDK
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 4. Internal
import com.korur.oba.connect.retry.enums.ConnectRetryPolicy;

/**
 * Marks a method for synchronous retry with exponential backoff.
 *
 * <p>The retry behavior (max attempts, delay, multiplier, exception classification)
 * is determined by the specified {@link ConnectRetryPolicy}.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * {@literal @}ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
 * public String uploadFile(String fileName, byte[] content) {
 *     return blobClient.upload(fileName, content);
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConnectRetry {

    /**
     * The retry policy to apply, defining all retry parameters.
     */
    ConnectRetryPolicy value();
}
