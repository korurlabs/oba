package com.korur.oba.apps.kaya.plan.repository;

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
import com.korur.oba.apps.kaya.plan.entity.PlanVersion;

@Repository
public interface PlanVersionRepository extends JpaRepository<PlanVersion, UUID>, JpaSpecificationExecutor<PlanVersion> {

    Optional<PlanVersion> findByPlanIdAndNumber(UUID planId, int number);

    /** The latest version of a plan, whose hash decides whether a new capture adds a version. */
    Optional<PlanVersion> findFirstByPlanIdOrderByNumberDesc(UUID planId);

    List<PlanVersion> findByPlanIdOrderByNumberAsc(UUID planId);
}
