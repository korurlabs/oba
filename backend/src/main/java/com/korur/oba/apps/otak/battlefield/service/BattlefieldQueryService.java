package com.korur.oba.apps.otak.battlefield.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.dto.response.RelationListResponse;

/**
 * Read service interface for the battlefield: every lead and customer of an organization
 * in one view.
 */
public interface BattlefieldQueryService {

    /**
     * Reads the battlefield: every lead and customer of the organization, every stage, by name.
     *
     * @param organizationId the organization that owns the relations
     * @return the leads and customers in name order
     */
    List<RelationListResponse> getBattlefield(UUID organizationId);
}
