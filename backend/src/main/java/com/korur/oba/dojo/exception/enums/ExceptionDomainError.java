package com.korur.oba.dojo.exception.enums;

/**
 * Marker interface for domain-specific exception error enums.
 *
 * <p>All domain error enums (e.g., {@code ExceptionDomainKaizenError},
 * {@code ExceptionDomainOutboxError}) implement this interface in addition
 * to {@link ExceptionError}.</p>
 *
 * <p>This enables collecting or filtering domain errors programmatically
 * when needed (e.g., for metrics, dashboards, or domain-scoped logging).</p>
 */
public interface ExceptionDomainError extends ExceptionError {
}
