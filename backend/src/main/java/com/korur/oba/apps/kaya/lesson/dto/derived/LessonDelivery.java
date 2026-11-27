package com.korur.oba.apps.kaya.lesson.dto.derived;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryResponse;

/**
 * One page of the titles a checkout reads with the ETag that stands for all its lessons, so the
 * controller can answer 304 to a CLI that already holds them.
 *
 * @param lessons the page, the response body with its pagination
 * @param etag    the unquoted ETag value of every applicable lesson, the same on every page
 */
public record LessonDelivery(Page<LessonDeliveryResponse> lessons, String etag) {
}
