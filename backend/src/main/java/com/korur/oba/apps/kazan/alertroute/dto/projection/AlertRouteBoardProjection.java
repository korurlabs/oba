package com.korur.oba.apps.kazan.alertroute.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The system key of an alert route with the id of its board, read straight from
 * the table without loading the board entity.
 */
public interface AlertRouteBoardProjection {

    String getSystemKey();

    UUID getBoardId();
}
