package com.korur.oba.apps.kaya.lesson.dto.derived;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryResponse;

/**
 * The titles a checkout reads with the ETag that stands for its lessons, so the controller can
 * answer 304 to a CLI that already holds them.
 *
 * @param lessons the response body
 * @param etag    the unquoted ETag value
 */
public record LessonDelivery(List<LessonDeliveryResponse> lessons, String etag) {
}
