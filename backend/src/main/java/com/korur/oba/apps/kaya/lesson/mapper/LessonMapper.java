package com.korur.oba.apps.kaya.lesson.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.dto.derived.LessonDelivery;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryProjection;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryRequest;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryCreatedResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliverySearchResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDetailResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonFiltersResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonListResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.embedded.LessonReferenceResponse;
import com.korur.oba.apps.kaya.lesson.dto.response.embedded.LessonSessionResponse;
import com.korur.oba.apps.kaya.lesson.entity.Lesson;
import com.korur.oba.apps.kaya.session.entity.Session;

/**
 * Maps lessons to responses and requests to lessons. A lesson keeps plain ids of other domains, so
 * the names of its repository and author and its session are resolved by the caller, for a whole
 * page at once, and passed in.
 */
@Mapper(componentModel = "spring")
public interface LessonMapper {

    LessonReferenceResponse toReferenceResponse(UUID id, String name);

    LessonSessionResponse toSessionResponse(Session session);

    @Mapping(target = "id", source = "lesson.id")
    @Mapping(target = "title", source = "lesson.title")
    @Mapping(target = "repository", source = "repository")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "session", source = "session")
    @Mapping(target = "canEdit", source = "canEdit")
    LessonListResponse toListResponse(Lesson lesson, LessonReferenceResponse repository, LessonReferenceResponse user,
            LessonSessionResponse session, boolean canEdit);

    @Mapping(target = "id", source = "lesson.id")
    @Mapping(target = "title", source = "lesson.title")
    @Mapping(target = "repository", source = "repository")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "session", source = "session")
    @Mapping(target = "canEdit", source = "canEdit")
    LessonDetailResponse toDetailResponse(Lesson lesson, LessonReferenceResponse repository, LessonReferenceResponse user,
            LessonSessionResponse session, boolean canEdit);

    LessonFiltersResponse toFiltersResponse(List<LessonReferenceResponse> repositories, List<LessonReferenceResponse> users);

    LessonDeliveryResponse toDeliveryResponse(LessonDeliveryProjection lesson, String repository, boolean changed);

    LessonDelivery toDelivery(List<LessonDeliveryResponse> lessons, String etag);

    LessonDeliverySearchResponse toDeliverySearchResponse(Lesson lesson, String repository);

    LessonDeliveryCreatedResponse toDeliveryCreatedResponse(UUID id, String url);

    @Mapping(target = "title", source = "request.title")
    @Mapping(target = "body", source = "request.body")
    @Mapping(target = "repositoryId", source = "repositoryId")
    LessonRequest toLessonRequest(LessonDeliveryRequest request, UUID repositoryId);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "title", source = "request.title")
    @Mapping(target = "body", source = "request.body")
    @Mapping(target = "repositoryId", source = "request.repositoryId")
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "claudeSessionId", source = "claudeSessionId")
    Lesson toEntity(LessonRequest request, UUID organizationId, UUID userId, String claudeSessionId);

    /** A full update: a null repository moves the lesson to the whole organization. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "claudeSessionId", ignore = true)
    void updateEntity(@MappingTarget Lesson lesson, LessonRequest request);
}
