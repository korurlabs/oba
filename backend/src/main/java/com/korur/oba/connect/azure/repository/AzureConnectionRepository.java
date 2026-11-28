package com.korur.oba.connect.azure.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party
import jakarta.persistence.LockModeType;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.enums.AzureConnectionStatus;

/**
 * Repository interface for {@link AzureConnection} entity database operations.
 *
 * Every read on behalf of a user goes through the organization id, so a connection of another
 * organization reads as absent. A check writes its result under the row lock of
 * {@link #findByIdForUpdate}, so a check and an admin's edit of the identity never interleave.
 */
@Repository
public interface AzureConnectionRepository extends JpaRepository<AzureConnection, UUID>,
        JpaSpecificationExecutor<AzureConnection> {

    /**
     * Finds a connection of one organization.
     *
     * @param id             ID of the connection
     * @param organizationId ID of the organization the connection must belong to
     * @return the connection, empty when it does not exist or belongs to another organization
     */
    Optional<AzureConnection> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Loads a connection with a row lock held until the transaction ends, so the result of a check
     * is written against the identity the row holds at that moment.
     *
     * @param id ID of the connection
     * @return the locked connection, or empty when it was deleted
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AzureConnection c WHERE c.id = :id")
    Optional<AzureConnection> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Lists the connections of one organization in name order.
     *
     * @param organizationId ID of the organization
     * @return the organization's connections
     */
    List<AzureConnection> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

    /**
     * Tells whether the organization has a connection with the name.
     *
     * @param organizationId ID of the organization
     * @param name           the name
     * @return true when the name is taken
     */
    boolean existsByOrganizationIdAndName(UUID organizationId, String name);

    /**
     * Tells whether another connection of the organization has the name, for a rename.
     *
     * @param organizationId ID of the organization
     * @param name           the name
     * @param id             ID of the connection being renamed, left out of the check
     * @return true when another connection has the name
     */
    boolean existsByOrganizationIdAndNameAndIdNot(UUID organizationId, String name, UUID id);

    /**
     * Tells whether the organization already connects the identity. Called only with a client id:
     * connections without one never collide, as in the unique constraint.
     *
     * @param organizationId ID of the organization
     * @param tenantId       the tenant id
     * @param clientId       the client id, not null
     * @return true when a connection names the same tenant and client id
     */
    boolean existsByOrganizationIdAndTenantIdAndClientId(UUID organizationId, UUID tenantId, UUID clientId);

    /**
     * Tells whether another connection of the organization connects the identity, for an update.
     *
     * @param organizationId ID of the organization
     * @param tenantId       the tenant id
     * @param clientId       the client id, not null
     * @param id             ID of the connection being updated, left out of the check
     * @return true when another connection names the same tenant and client id
     */
    boolean existsByOrganizationIdAndTenantIdAndClientIdAndIdNot(UUID organizationId, UUID tenantId,
                                                                 UUID clientId, UUID id);

    /**
     * Lists the IDs of the connections in the given statuses, in ID order, for the daily check.
     * The status is bound as a parameter because the column is a PostgreSQL enum, which takes the
     * parameter's type but not a JPQL enum literal.
     *
     * @param statuses the statuses to include
     * @return IDs of the matching connections
     */
    @Query("SELECT c.id FROM AzureConnection c WHERE c.status IN :statuses ORDER BY c.id")
    List<UUID> findIdsByStatusIn(@Param("statuses") Collection<AzureConnectionStatus> statuses);
}
