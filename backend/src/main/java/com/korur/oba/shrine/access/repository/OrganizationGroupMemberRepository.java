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
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;

/**
 * Repository for the member rows of custom organization groups. Derived queries skip deleted rows.
 */
@Repository
public interface OrganizationGroupMemberRepository extends SoftDeleteRepository<OrganizationGroupMember, UUID> {

    /**
     * Lists the member rows of a group that are not deleted.
     *
     * @param groupId the group identifier
     * @return the member rows, in no particular order
     */
    List<OrganizationGroupMember> findByGroup_Id(UUID groupId);

    /**
     * Finds the member row of a user in a group that is not deleted.
     *
     * @param groupId the group identifier
     * @param userId  the user identifier
     * @return the member row, or empty when the user is not in the group
     */
    Optional<OrganizationGroupMember> findByGroup_IdAndUser_Id(UUID groupId, UUID userId);

    /**
     * Tells whether a user is in a group, among the rows that are not deleted.
     *
     * @param groupId the group identifier
     * @param userId  the user identifier
     * @return true when the user is in the group
     */
    boolean existsByGroup_IdAndUser_Id(UUID groupId, UUID userId);

    /**
     * Counts the member rows of a group that are not deleted.
     *
     * @param groupId the group identifier
     * @return the number of members
     */
    long countByGroup_Id(UUID groupId);

    /**
     * Lists the member rows of a user in the groups of one organization that are not deleted; used when the
     * user leaves or is removed from the organization.
     *
     * @param organizationId the organization identifier
     * @param userId         the user identifier
     * @return the member rows, in no particular order
     */
    List<OrganizationGroupMember> findByGroup_Organization_IdAndUser_Id(UUID organizationId, UUID userId);
}
