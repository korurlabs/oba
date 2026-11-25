package com.korur.oba.apps.kaya.lesson.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryStateProjection;

/**
 * The ETag of the lessons a checkout reads.
 */
public final class LessonEtagHelper {

    private LessonEtagHelper() {
    }

    /**
     * The ETag of every applicable lesson, not only of the page a response carries, so an add, an
     * edit or a delete changes the ETag wherever the lesson sits in the list, and every page of one
     * state of the lessons carries the same ETag. The digest comes from
     * {@link com.korur.oba.apps.kaya.lesson.repository.LessonRepository#findDeliveryState}; the form
     * prefix sets it apart from every ETag an earlier computation produced.
     *
     * @param state the count and digest of the applicable lessons
     * @return the form prefix and 64 hex characters, unquoted
     */
    public static String etagOf(LessonDeliveryStateProjection state) {
        return LessonConstant.ETAG_FORM + state.getDigest();
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
