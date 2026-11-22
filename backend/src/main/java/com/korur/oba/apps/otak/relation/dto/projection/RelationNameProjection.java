package com.korur.oba.apps.otak.relation.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The id and name of one relation, a row of
 * {@link com.korur.oba.apps.otak.relation.repository.RelationRepository#findNames}, read for a
 * page of documents without loading the relation entities.
 */
public interface RelationNameProjection {

    /**
     * @return the relation id
     */
    UUID getId();

    /**
     * @return the relation name
     */
    String getName();
}
