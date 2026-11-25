package com.korur.oba.apps.kaya.lesson.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The lessons a checkout reads, summed up by the database without sending a row: how many there
 * are, and a digest that changes with every add, edit, move or delete among them.
 */
public interface LessonDeliveryStateProjection {

    Long getTotal();

    String getDigest();
}
