package com.korur.oba.shrine.organization.repository;

// 1. Framework-level
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.repository.SoftDeleteRepository;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

/**
 * Repository for CRUD operations on organizations.
 * Extends SoftDeleteRepository for soft-delete support.
 */
@Repository
public interface OrganizationRepository extends SoftDeleteRepository<Organization, UUID> {

    /**
     * Tells whether an organization that is not deleted has the given status.
     *
     * @param id     the organization identifier
     * @param status the status the organization must have
     * @return true when the organization exists, is not deleted and has the status
     */
    boolean existsByIdAndStatus(UUID id, OrganizationStatus status);
}
