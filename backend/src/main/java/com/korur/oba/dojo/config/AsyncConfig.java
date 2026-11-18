package com.korur.oba.dojo.config;

// 1. Framework-level
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 3. Java SDK
import java.util.concurrent.Executor;

/**
 * Turns on @Async and retries, with the executors the platform and its apps send work to.
 *
 * Class-based proxies keep a bean's own methods that its interface does not declare, such as the
 * scheduled heartbeat of Kaya's live session stream, reachable through the proxy that @Async adds.
 */
@Configuration
@EnableAsync(proxyTargetClass = true)
@EnableRetry
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notification-");
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated pool for ICP VIES validation calls. Sized small because the
     * upstream VIES SOAP service rate-limits aggressive callers; the queue
     * absorbs short bursts when an ICP declaration with many lines is
     * submitted. A non-blocking submission path remains the goal -- each line
     * is validated on a worker thread while the user gets an immediate
     * SUBMITTED response.
     */
    @Bean(name = "viesValidationExecutor")
    public Executor viesValidationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("vies-validation-");
        executor.initialize();
        return executor;
    }
}
