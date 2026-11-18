package com.korur.oba.apps.kalkan.page.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageTreeProjection;
import com.korur.oba.apps.kalkan.page.dto.request.KalkanPageTranslationRequest;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageDetailResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageListResponse;
import com.korur.oba.apps.kalkan.page.dto.response.KalkanPageUpsertResponse;
import com.korur.oba.apps.kalkan.page.dto.response.embedded.KalkanPageTranslationEmbeddedResponse;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.page.entity.KalkanPageRevision;
import com.korur.oba.apps.kalkan.page.entity.KalkanPageTranslation;
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;
import com.korur.oba.apps.kalkan.page.enums.KalkanUpsertOutcome;
import com.korur.oba.apps.kalkan.permission.enums.KalkanLevel;
import com.korur.oba.connect.azure.service.BlobUrlRewriterMapper;

/**
 * Maps {@link KalkanPage}s, their {@link KalkanPageTranslation}s and tree rows to responses, and
 * translation requests to new translations. Fields only: the page, its parent, its root and the
 * acting user are set by {@link KalkanPageMapperHelper}.
 *
 * A translation body is stored with unsigned blob URLs, so an unchanged push leaves it unchanged; the
 * blob container is private, so {@link BlobUrlRewriterMapper} signs those URLs on the way out only.
 * Entities and revisions take the body as written.
 */
@Mapper(componentModel = "spring", uses = { BlobUrlRewriterMapper.class })
public interface KalkanPageMapper {

    @Mapping(target = "id", source = "page.id")
    @Mapping(target = "parentId", source = "page.parent.id")
    @Mapping(target = "rootId", source = "page.rootId")
    @Mapping(target = "slug", source = "page.slug")
    @Mapping(target = "path", source = "path")
    @Mapping(target = "compliance", source = "page.compliance")
    @Mapping(target = "version", source = "page.version")
    @Mapping(target = "level", source = "level")
    @Mapping(target = "translations", source = "translations")
    @Mapping(target = "updatedAt", source = "page.updatedAt")
    KalkanPageDetailResponse toDetailResponse(KalkanPage page, String path, KalkanLevel level,
                                              List<KalkanPageTranslationEmbeddedResponse> translations);

    @Mapping(target = "locale", source = "translation.locale")
    @Mapping(target = "title", source = "translation.title")
    @Mapping(target = "body", source = "translation.body", qualifiedByName = "rewriteMarkdownBlobUrls")
    @Mapping(target = "updatedAt", source = "translation.updatedAt")
    @Mapping(target = "updatedByName", source = "updatedByName")
    KalkanPageTranslationEmbeddedResponse toTranslationEmbeddedResponse(KalkanPageTranslation translation,
                                                                        String updatedByName);

    @Mapping(target = "id", source = "row.id")
    @Mapping(target = "parentId", source = "row.parentId")
    @Mapping(target = "rootId", source = "row.rootId")
    @Mapping(target = "slug", source = "row.slug")
    @Mapping(target = "path", source = "row.path")
    @Mapping(target = "position", source = "row.position")
    @Mapping(target = "compliance", source = "row.compliance")
    @Mapping(target = "titles", source = "titles")
    @Mapping(target = "level", source = "level")
    KalkanPageListResponse toListResponse(KalkanPageTreeProjection row, Map<KalkanLocale, String> titles,
                                          KalkanLevel level);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "page", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "updatedById", ignore = true)
    @Mapping(target = "locale", source = "locale")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "body", source = "body")
    KalkanPageTranslation toTranslationEntity(KalkanPageTranslationRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "pageId", source = "translation.page.id")
    @Mapping(target = "locale", source = "translation.locale")
    @Mapping(target = "title", source = "translation.title")
    @Mapping(target = "body", source = "translation.body")
    @Mapping(target = "pageVersion", source = "pageVersion")
    @Mapping(target = "createdById", source = "translation.updatedById")
    KalkanPageRevision toRevision(KalkanPageTranslation translation, long pageVersion);

    /**
     * Writes a request's title and body onto a stored translation of the same locale.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "page", ignore = true)
    @Mapping(target = "locale", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "updatedById", ignore = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "body", source = "body")
    void updateTranslationEntity(@MappingTarget KalkanPageTranslation translation, KalkanPageTranslationRequest request);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "path", source = "path")
    @Mapping(target = "outcome", source = "outcome")
    @Mapping(target = "version", source = "version")
    KalkanPageUpsertResponse toUpsertResponse(UUID id, String path, KalkanUpsertOutcome outcome, long version);
}
