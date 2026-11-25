package com.korur.oba.connect.routine.core.config;

// 1. Framework-level
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.concurrent.Executor;

// 4. Internal
import com.korur.oba.connect.routine.core.constant.RoutineConstant;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.routine.core.constant.config.RoutineConfigLogConstant.*;

/**
 * Configuration of the vendor-neutral part of the routine connector: the executor that fires run on.
 */
@Configuration
@RequiredArgsConstructor
public class RoutineConfig {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RoutineConfig.class);

    private final LoggingService loggingService;

    /**
     * Executor of routine fires, so a vendor call, which may wait up to the read timeout, never runs
     * on a request thread.
     *
     * Two threads at most: vendors allow a few fires an hour per routine, so a larger pool would only
     * meet more rate limits. When the queue is full the fire is dropped and logged at error level
     * instead of failing the caller, whose change is already committed.
     *
     * @return the fire executor
     */
    @Bean(name = RoutineConstant.EXECUTOR_BEAN_NAME)
    public Executor routineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(RoutineConstant.EXECUTOR_CORE_POOL_SIZE);
        executor.setMaxPoolSize(RoutineConstant.EXECUTOR_MAX_POOL_SIZE);
        executor.setQueueCapacity(RoutineConstant.EXECUTOR_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(RoutineConstant.EXECUTOR_THREAD_PREFIX);
        executor.setRejectedExecutionHandler((task, pool) -> loggingService.error(LOG_PREFIX, LogLayer.CONFIG,
                EXECUTOR_REJECTED, RoutineConstant.EXECUTOR_QUEUE_CAPACITY));
        executor.initialize();
        return executor;
    }
}
