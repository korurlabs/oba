package com.korur.oba.shrine.organization.repository;

// 1. Framework-level
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.repository.SoftDeleteRepository;
import com.korur.oba.shrine.organization.entity.Organization;

/**
 * Repository for CRUD operations on organizations.
 * Extends SoftDeleteRepository for soft-delete support.
 */
@Repository
public interface OrganizationRepository extends SoftDeleteRepository<Organization, UUID> {
}
