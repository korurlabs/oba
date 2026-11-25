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
     * Hashes the id and updatedAt of every applicable lesson, not only of the page a response
     * carries, so an add, an edit or a delete changes the ETag wherever the lesson sits in the list,
     * and every page of one state of the lessons carries the same ETag.
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

    /**
     * Whether the ETag a client sent back names the lessons as they are now. The client may send
     * the ETag header as it received it, in quotes, or its bare value.
     *
     * @param sentEtag the ETag the client sent, bare or quoted
     * @param etag     the current ETag, unquoted
     * @return true when both name the same state of the lessons
     */
    public static boolean matches(String sentEtag, String etag) {
        String bare = sentEtag.startsWith(LessonConstant.ETAG_QUOTE) && sentEtag.endsWith(LessonConstant.ETAG_QUOTE)
                && sentEtag.length() > 1
                ? sentEtag.substring(1, sentEtag.length() - 1)
                : sentEtag;
        return bare.equals(etag);
    }
}
