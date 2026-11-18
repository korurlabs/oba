package com.korur.oba.dojo.config;

// 1. Framework-level
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// 4. Internal
import com.korur.oba.dojo.repository.impl.SoftDeleteRepositoryImpl;

/**
 * JPA repository configuration for the shared PostgreSQL model.
 *
 * Enables Spring Data JPA repository scanning across the whole application
 * package tree and installs the soft-delete aware repository base class.
 *
 * @see com.korur.oba.dojo.repository.impl.SoftDeleteRepositoryImpl
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.korur.oba",
    repositoryBaseClass = SoftDeleteRepositoryImpl.class
)
public class JpaRepositoryConfig {
}
