package com.korur.oba.shrine.organization.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
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
     * Reads the status of an organization that is not deleted, without loading the organization.
     *
     * @param id the organization identifier
     * @return the status, or empty when the organization does not exist or is deleted
     */
    @Query("SELECT o.status FROM Organization o WHERE o.id = :id")
    Optional<OrganizationStatus> findStatusById(@Param("id") UUID id);
}
