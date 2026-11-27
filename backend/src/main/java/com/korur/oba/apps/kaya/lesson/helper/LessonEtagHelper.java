package com.korur.oba.apps.kaya.lesson.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryStateProjection;

/**
 * The ETags of the lessons a checkout reads. Both forms carry one digest of every applicable
 * lesson, not only of the page a response carries, so an add, an edit or a delete changes them
 * wherever the lesson sits in the list. The digest comes from
 * {@link com.korur.oba.apps.kaya.lesson.repository.LessonRepository#findDeliveryState}; the form
 * prefix names what the answer holds, see {@link LessonConstant#ETAG_PAGED_FORM}.
 */
public final class LessonEtagHelper {

    private LessonEtagHelper() {
    }

    /**
     * The ETag of every applicable lesson read page by page, the same on every page of one state of
     * the lessons.
     *
     * @param state the count and digest of the applicable lessons
     * @return the paged form prefix and 64 hex characters, unquoted
     */
    public static String pagedEtagOf(LessonDeliveryStateProjection state) {
        return LessonConstant.ETAG_PAGED_FORM + state.getDigest();
    }

    /**
     * The ETag of the answer to a request without page, which holds only the newest lessons. It
     * changes with the lessons exactly as {@link #pagedEtagOf} does, but never equals it.
     *
     * @param state the count and digest of the applicable lessons
     * @return the unpaged form prefix and 64 hex characters, unquoted
     */
    public static String unpagedEtagOf(LessonDeliveryStateProjection state) {
        return LessonConstant.ETAG_UNPAGED_FORM + state.getDigest();
    }

    /**
     * Whether the ETag a client sent back names the lessons as they are now. The client may send
     * the ETag header as it received it, in quotes, or its bare value. A proxy that compresses the
     * answer marks the header weak with W/, so the comparison is the weak one of RFC 9110 section
     * 8.8.3.2: the W/ is dropped and the values are compared. The form prefix stays part of the
     * value, so a weak unpaged ETag never matches the paged one.
     *
     * @param sentEtag the ETag the client sent, bare, quoted or weak
     * @param etag     the current ETag, unquoted
     * @return true when both name the same state of the lessons
     */
    public static boolean matches(String sentEtag, String etag) {
        String strong = sentEtag.startsWith(LessonConstant.ETAG_WEAK_PREFIX)
                ? sentEtag.substring(LessonConstant.ETAG_WEAK_PREFIX.length())
                : sentEtag;
        String bare = strong.startsWith(LessonConstant.ETAG_QUOTE) && strong.endsWith(LessonConstant.ETAG_QUOTE)
                && strong.length() > 1
                ? strong.substring(1, strong.length() - 1)
                : strong;
        return bare.equals(etag);
    }
}
