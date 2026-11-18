package com.korur.oba.apps.kaya.seed.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.seed.entity.SeedRun;

/**
 * Reads and writes {@link SeedRun} rows and takes the organization's seed lock, so two seeds of one
 * organization run one after the other. Used only by the seed domain's service and its run recorder.
 */
@Repository
public interface SeedRunRepository extends JpaRepository<SeedRun, UUID>, JpaSpecificationExecutor<SeedRun> {

    /**
     * Serialises the seed runs of an organization until the caller's transaction ends. Two runs at
     * once would otherwise both find a set missing and both create it, or both number a version.
     *
     * @param lockKey the key of the lock, derived from the organization id
     * @return always 1
     */
    @Query(value = "SELECT count(*) FROM (SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) AS seed_lock",
            nativeQuery = true)
    long lockSeeds(@Param("lockKey") String lockKey);
}
