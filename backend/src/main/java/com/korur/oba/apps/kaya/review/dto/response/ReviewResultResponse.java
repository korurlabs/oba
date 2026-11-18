package com.korur.oba.apps.kaya.review.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.release.dto.response.ReleaseResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;

/**
 * What a review led to: the review, the version after it and the releases an approval made.
 */
@Schema(description = "The outcome of a review: the recorded review, the version after it and, on approval, the new releases")
public record ReviewResultResponse(
        /**
 * What a review led to: the review, the version after it and the releases an approval made.
 */
@Schema(description = "The review just recorded")
        ReviewResponse review,

        /**
 * What a review led to: the review, the version after it and the releases an approval made.
 */
@Schema(description = "The version after the review: APPROVED, DRAFT after a rejection, or still IN_REVIEW")
        VersionResponse version,

        /**
 * What a review led to: the review, the version after it and the releases an approval made.
 */
@Schema(description = "One signed release per composing repository that delivers anything; empty unless approved")
        List<ReleaseResponse> releases
) {
}
