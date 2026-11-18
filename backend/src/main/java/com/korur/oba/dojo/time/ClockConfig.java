package com.korur.oba.dojo.time;

// 1. Framework-level
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 2. Third-party

// 3. Java SDK
import java.time.Clock;

/**
 * Provides the clock that time-driven jobs read the current time from.
 *
 * The clock is pinned to UTC rather than the JVM zone, so a laptop in another
 * zone sees the same moments as production. Injecting it lets tests run a job
 * at a fixed time.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
