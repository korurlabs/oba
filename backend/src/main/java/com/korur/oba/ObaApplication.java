package com.korur.oba;

// 1. Framework-level
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Oba backend.
 *
 * The own {@link ComponentScan} gives every bean its fully qualified class name, because
 * several modules hold classes with the same simple name. It replaces the scan that
 * {@link SpringBootApplication} declares, so it repeats that scan's exclude filters: without
 * {@link TypeExcludeFilter} the test configurations and test controllers under the base package
 * would be picked up by every Spring Boot test context, and without
 * {@link AutoConfigurationExcludeFilter} auto-configuration classes would be scanned as plain
 * configuration.
 */
@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
        })
@EnableRetry
public class ObaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObaApplication.class, args);
    }
}
