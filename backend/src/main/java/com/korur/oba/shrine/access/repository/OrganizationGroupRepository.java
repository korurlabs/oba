package com.korur.oba.shrine.access.repository;

// 1. Framework-level
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.repository.SoftDeleteRepository;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;

/**
 * Repository for organization groups. Derived queries skip deleted groups.
 */
@Repository
public interface OrganizationGroupRepository extends SoftDeleteRepository<OrganizationGroup, UUID> {

    /**
     * Lists the groups of an organization that are not deleted.
     *
     * @param organizationId the organization identifier
     * @return the groups, in no particular order
     */
    List<OrganizationGroup> findByOrganization_Id(UUID organizationId);

    /**
     * Finds a group that is not deleted only when it belongs to the given organization, so a group id of
     * another organization reads as missing.
     *
     * @param id             the group identifier
     * @param organizationId the organization identifier
     * @return the group, or empty when it is missing, deleted or of another organization
     */
    Optional<OrganizationGroup> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    /**
     * Finds the group of an organization of the given type that is not deleted. For
     * {@link OrganizationGroupType#EVERYONE} there is at most one.
     *
     * @param organizationId the organization identifier
     * @param type           the group type
     * @return the group, or empty when the organization has none
     */
    Optional<OrganizationGroup> findFirstByOrganization_IdAndType(UUID organizationId, OrganizationGroupType type);

    /**
     * Tells whether a group that is not deleted already has the name in the organization, whatever its
     * case, as the unique index on lower(name) does.
     *
     * @param organizationId the organization identifier
     * @param name           the group name
     * @return true when the name is taken
     */
    boolean existsByOrganization_IdAndNameIgnoreCase(UUID organizationId, String name);

    /**
     * Tells whether another group that is not deleted already has the name in the organization, whatever
     * its case; used on a rename, where the group may keep its own name.
     *
     * @param organizationId the organization identifier
     * @param name           the group name
     * @param id             the group being renamed
     * @return true when the name is taken by another group
     */
    boolean existsByOrganization_IdAndNameIgnoreCaseAndIdNot(UUID organizationId, String name, UUID id);
}
