package com.korur.oba.apps.kaizen.sprint.repository;

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
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeriesBoard;

/**
 * Repository interface for SprintSeriesBoard entity database operations.
 */
@Repository
public interface SprintSeriesBoardRepository extends JpaRepository<SprintSeriesBoard, UUID>,
        JpaSpecificationExecutor<SprintSeriesBoard> {

    /**
     * Lists the boards of a series in link order.
     *
     * @param seriesId ID of the series
     * @return the series' boards by position
     */
    List<SprintSeriesBoard> findBySeries_IdOrderByPositionAsc(UUID seriesId);

    /**
     * Finds the series row of a board; a board belongs to at most one series.
     *
     * @param boardId ID of the board
     * @return the board's series row, empty when the board is in no series
     */
    Optional<SprintSeriesBoard> findByBoard_Id(UUID boardId);
}
