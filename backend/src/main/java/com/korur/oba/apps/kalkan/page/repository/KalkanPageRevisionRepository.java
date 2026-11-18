package com.korur.oba.apps.kalkan.page.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.entity.KalkanPageRevision;

/**
 * Repository of {@link KalkanPageRevision}. Revisions are only inserted.
 */
@Repository
public interface KalkanPageRevisionRepository extends JpaRepository<KalkanPageRevision, UUID> {
}
