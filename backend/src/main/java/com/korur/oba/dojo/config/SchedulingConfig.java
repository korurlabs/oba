package com.korur.oba.dojo.config;

// 1. Framework-level
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The scheduler of the application's {@link org.springframework.scheduling.annotation.Scheduled}
 * jobs.
 *
 * Spring Boot builds this bean by itself only while no other
 * {@link org.springframework.scheduling.TaskScheduler} exists. The STOMP broker defines its own
 * ({@link org.springframework.messaging.simp.config.AbstractMessageBrokerConfiguration#messageBrokerTaskScheduler}),
 * so without this bean Boot would back off and the jobs would move onto the broker's pool.
 * Defining it under the name {@link ScheduledAnnotationBeanPostProcessor} looks for keeps them on
 * Boot's default: one thread named scheduling-, built from the same
 * {@link ThreadPoolTaskSchedulerBuilder} and so still tuned by the spring.task.scheduling
 * properties.
 */
@Configuration
public class SchedulingConfig {

    @Bean(name = ScheduledAnnotationBeanPostProcessor.DEFAULT_TASK_SCHEDULER_BEAN_NAME)
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder threadPoolTaskSchedulerBuilder) {
        return threadPoolTaskSchedulerBuilder.build();
    }
}
