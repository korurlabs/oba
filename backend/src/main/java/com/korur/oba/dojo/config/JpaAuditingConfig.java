package com.korur.oba.dojo.config;

// 1. Framework-level
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// 2. Third-party

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.dojo.time.ClockConfig;

/**
 * Enables JPA auditing, which fills the created and updated timestamps of every
 * {@link BaseEntity}.
 *
 * The timestamps come from the {@link ClockConfig} clock rather than Spring Data's default
 * provider, which reads a LocalDateTime in the host zone. The audit columns then follow the
 * same UTC "now" as the rest of the backend, and a test that fixes the clock fixes them too.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
