package com.korur.oba.apps.otak.icp.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.icp.entity.Icp;

/**
 * Repository interface for {@link Icp} entity database operations.
 */
@Repository
public interface IcpRepository extends JpaRepository<Icp, UUID>, JpaSpecificationExecutor<Icp> {

    /**
     * Reads one ICP within an organization, so an ICP of another organization is not found.
     *
     * @param id             the ICP identifier
     * @param organizationId the organization that owns the ICP
     * @return the ICP, or empty when the organization has no ICP with the id
     */
    Optional<Icp> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Tells whether the organization already has an ICP with the name, case ignored.
     * Used on create, where no ICP is excluded.
     *
     * @param organizationId the organization that owns the ICPs
     * @param name           the name to look for
     * @return true when an ICP of the organization has the name
     */
    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    /**
     * Tells whether another ICP of the organization has the name, case ignored. Used on
     * update, where the ICP being changed may keep its own name.
     *
     * @param organizationId the organization that owns the ICPs
     * @param name           the name to look for
     * @param id             the ICP left out of the check
     * @return true when an ICP of the organization other than the given one has the name
     */
    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(UUID organizationId, String name, UUID id);
}
