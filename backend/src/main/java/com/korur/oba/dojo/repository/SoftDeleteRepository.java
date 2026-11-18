package com.korur.oba.dojo.repository;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;

/**
 * Base repository interface for entities that support soft-delete semantics.
 * <p>
 * Extends JpaRepository to provide standard CRUD operations while adding
 * methods to query soft-deleted records, restore them, and perform
 * hard deletes without cascading to related entities.
 * <p>
 * Annotated with {@link NoRepositoryBean} since this is a base interface
 * that should not be instantiated directly by Spring Data.
 *
 * @param <T>  the entity type
 * @param <ID> the entity ID type
 */
@NoRepositoryBean
public interface SoftDeleteRepository<T, ID> extends JpaRepository<T, ID> {

    /**
     * Finds an entity by its ID, including soft-deleted records.
     *
     * @param id the entity ID
     * @return optional containing the entity if found, including deleted ones
     */
    Optional<T> findByIdWithDeleted(ID id);

    /**
     * Finds all entities, including soft-deleted records.
     *
     * @return list of all entities including deleted ones
     */
    List<T> findAllWithDeleted();

    /**
     * Finds all entities with pagination, including soft-deleted records.
     *
     * @param pageable pagination parameters
     * @return page of entities including deleted ones
     */
    Page<T> findAllWithDeleted(Pageable pageable);

    /**
     * Finds all soft-deleted entities.
     *
     * @return list of only soft-deleted entities
     */
    List<T> findAllDeleted();

    /**
     * Finds all soft-deleted entities with pagination.
     *
     * @param pageable pagination parameters
     * @return page of only soft-deleted entities
     */
    Page<T> findAllDeleted(Pageable pageable);

    /**
     * Restores a soft-deleted entity by its ID.
     *
     * @param id the entity ID to restore
     */
    void restoreById(ID id);

    /**
     * Restores multiple soft-deleted entities by their IDs.
     *
     * @param ids the entity IDs to restore
     */
    void restoreAllByIds(Iterable<ID> ids);

    /**
     * Permanently deletes an entity by its ID without cascading to related entities.
     *
     * @param id the entity ID to hard-delete
     */
    void deleteByIdWithoutRelations(ID id);
}
