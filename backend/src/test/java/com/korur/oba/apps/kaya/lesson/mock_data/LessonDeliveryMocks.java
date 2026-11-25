package com.korur.oba.apps.kaya.lesson.mock_data;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// 4. Internal
import com.korur.oba.apps.kaya.delivery.helper.DeliveryEtagHelper;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryProjection;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryStateProjection;

/**
 * The rows and the state the lesson repository answers for the delivery to a checkout.
 */
public final class LessonDeliveryMocks {

    private LessonDeliveryMocks() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** An organization-wide lesson, numbered newest first as the repository returns them. */
    public static LessonDeliveryProjection row(int number) {
        UUID id = new UUID(0L, number);
        Instant updatedAt = Instant.parse("2026-10-01T00:00:00Z").minusSeconds(number);
        return new LessonDeliveryProjection() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getTitle() {
                return "lesson " + number;
            }

            @Override
            public UUID getRepositoryId() {
                return null;
            }

            @Override
            public Instant getUpdatedAt() {
                return updatedAt;
            }
        };
    }

    /** Lessons 1 to count, newest first, in a list a test may change. */
    public static List<LessonDeliveryProjection> rows(int count) {
        return new ArrayList<>(IntStream.rangeClosed(1, count).mapToObj(LessonDeliveryMocks::row).toList());
    }

    /** What the database answers for these lessons: their count and a digest of every id and updatedAt. */
    public static LessonDeliveryStateProjection stateOf(List<LessonDeliveryProjection> lessons) {
        String digest = DeliveryEtagHelper.sha256Hex(lessons.stream()
                .map(lesson -> lesson.getId() + ":" + lesson.getUpdatedAt())
                .sorted()
                .collect(Collectors.joining("\n")));
        long total = lessons.size();
        return new LessonDeliveryStateProjection() {
            @Override
            public Long getTotal() {
                return total;
            }

            @Override
            public String getDigest() {
                return digest;
            }
        };
    }
}
