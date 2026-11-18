package com.korur.oba.connect.push.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.entity.PushPreference;

/**
 * Repository interface for {@link PushPreference} entity database operations.
 *
 * One row per user, enforced by the unique constraint push_preferences_user_id_uk. Two devices of a
 * user can save the settings at once, so the first save creates the row through
 * {@link #insertIfAbsent} in one statement and never fails on the constraint.
 */
@Repository
public interface PushPreferenceRepository
        extends JpaRepository<PushPreference, UUID>, JpaSpecificationExecutor<PushPreference> {

    /**
     * Finds the preferences of a user.
     *
     * @param userId ID of the user
     * @return the preferences, empty when the user never saved them
     */
    Optional<PushPreference> findByUserId(UUID userId);

    /**
     * Finds the preferences of the given users; users without a row are left out.
     *
     * @param userIds IDs of the users
     * @return the preferences, in no particular order
     */
    List<PushPreference> findAllByUserIdIn(Collection<UUID> userIds);

    /**
     * Creates a user's row with every switch on, the column defaults, and leaves an existing row
     * unchanged; the caller then loads the row and applies the update to it.
     *
     * @param userId ID of the user
     * @return 1 when the row was created, 0 when the user already had one
     */
    @Modifying
    @Query(value = """
            INSERT INTO shrine.push_preferences (id, user_id, created_at, updated_at)
            VALUES (gen_random_uuid(), :userId, now(), now())
            ON CONFLICT ON CONSTRAINT push_preferences_user_id_uk DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId);
}
