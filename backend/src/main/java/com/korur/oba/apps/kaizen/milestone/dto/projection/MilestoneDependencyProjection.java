package com.korur.oba.apps.kaizen.milestone.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * One dependency edge: the dependent milestone and the milestone it depends on.
 */
public interface MilestoneDependencyProjection {

    UUID getMilestoneId();

    UUID getDependsOnMilestoneId();
}
