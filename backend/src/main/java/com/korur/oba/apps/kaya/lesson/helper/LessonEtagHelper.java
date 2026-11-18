package com.korur.oba.apps.kaya.lesson.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.delivery.helper.DeliveryEtagHelper;
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryProjection;

/**
 * The ETag of the lessons a checkout reads.
 */
public final class LessonEtagHelper {

    private LessonEtagHelper() {
    }

    /**
     * Hashes the id and updatedAt of every applicable lesson, not only of the capped list a response
     * carries, so an add, an edit or a delete changes the ETag wherever the lesson sits in the list.
     *
     * @param lessons every lesson that applies to the checkout, in any order
     * @return 64 hex characters, unquoted
     */
    public static String etagOf(Collection<LessonDeliveryProjection> lessons) {
        return DeliveryEtagHelper.sha256Hex(lessons.stream()
                .map(lesson -> lesson.getId() + LessonConstant.ETAG_FIELD_SEPARATOR + lesson.getUpdatedAt())
                .sorted()
                .collect(Collectors.joining(LessonConstant.ETAG_LINE_SEPARATOR)));
    }
}
