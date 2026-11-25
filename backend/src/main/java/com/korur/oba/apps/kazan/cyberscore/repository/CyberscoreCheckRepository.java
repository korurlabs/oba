package com.korur.oba.apps.kazan.cyberscore.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckStatus;

/**
 * Repository interface for {@link CyberscoreCheck} entity database operations.
 *
 * Every lookup takes the organization of the request, so a check of another
 * organization reads as absent. The list queries sort in the database:
 * category is a PostgreSQL enum, which sorts in declaration order, and an
 * ascending sort puts null, the uncategorized checks, last.
 */
@Repository
public interface CyberscoreCheckRepository
        extends JpaRepository<CyberscoreCheck, UUID>, JpaSpecificationExecutor<CyberscoreCheck> {

    /**
     * Finds a check of the organization by ID.
     *
     * @param id             ID of the check
     * @param organizationId ID of the organization of the request
     * @return the check, or empty when the organization has no such check
     */
    Optional<CyberscoreCheck> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Finds all checks of the organization in display order: category, sort
     * order, then name.
     *
     * @param organizationId ID of the organization
     * @return the organization's checks
     */
    List<CyberscoreCheck> findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(UUID organizationId);

    /**
     * Finds the checks of the organization with the given status, in display order.
     *
     * @param organizationId ID of the organization
     * @param status         workflow status to filter by
     * @return the matching checks
     */
    List<CyberscoreCheck> findByOrganizationIdAndStatusOrderByCategoryAscSortOrderAscNameAsc(
            UUID organizationId, CyberscoreCheckStatus status);

    /**
     * Checks whether the organization already has a check with this name.
     *
     * @param organizationId ID of the organization
     * @param name           name to look for
     * @return true when the name is taken
     */
    boolean existsByOrganizationIdAndName(UUID organizationId, String name);

    /**
     * Checks whether another check of the organization has this name, for an edit.
     *
     * @param organizationId ID of the organization
     * @param name           name to look for
     * @param id             ID of the check being edited, which is left out
     * @return true when another check has the name
     */
    boolean existsByOrganizationIdAndNameAndIdNot(UUID organizationId, String name, UUID id);
}
