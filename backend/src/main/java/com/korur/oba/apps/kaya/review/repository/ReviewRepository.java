package com.korur.oba.apps.kaya.review.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.review.entity.Review;

/**
 * The {@link Review}s of versions, read by version in time order.
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID>, JpaSpecificationExecutor<Review> {

    List<Review> findByVersionIdOrderByCreatedAtAscIdAsc(UUID versionId);

    /** The reviews of a page of versions in one query, oldest first per version. */
    List<Review> findByVersionIdInOrderByCreatedAtAscIdAsc(Collection<UUID> versionIds);
}
