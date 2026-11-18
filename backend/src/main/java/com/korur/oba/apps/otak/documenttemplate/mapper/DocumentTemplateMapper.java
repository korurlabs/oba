package com.korur.oba.apps.otak.documenttemplate.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.documenttemplate.dto.request.DocumentTemplateCreateRequest;
import com.korur.oba.apps.otak.documenttemplate.dto.response.DocumentTemplateDetailResponse;
import com.korur.oba.apps.otak.documenttemplate.dto.response.DocumentTemplateListResponse;
import com.korur.oba.apps.otak.documenttemplate.entity.DocumentTemplate;
import com.korur.oba.connect.azure.service.BlobUrlRewriterMapper;

/**
 * Maps a {@link DocumentTemplateCreateRequest} to a new {@link DocumentTemplate}, and a
 * template to a {@link DocumentTemplateListResponse} or a
 * {@link DocumentTemplateDetailResponse}. MapStruct generates the implementation as a
 * Spring bean with constructor injection.
 *
 * A template has no association to another entity (its organization is a plain id), so
 * the mapper needs no MapperHelper to resolve ids into entities.
 *
 * Signed URLs. The rows store the unsigned URLs of the private blob container, so a stored
 * row never holds an expired signature. A browser can not open an unsigned URL of a
 * private container, so {@link BlobUrlRewriterMapper#rewriteBlobUrl} signs it with a
 * short-lived, read-only Shared Access Signature on the way out, in
 * {@link #toDetailResponse} only. A null URL, as on a template without files yet, stays
 * null.
 *
 * There is no update mapping: create and update write the trimmed name, the type and the
 * language through one helper of
 * {@link com.korur.oba.apps.otak.documenttemplate.service.impl.DocumentTemplateServiceImpl},
 * so both store the same columns the same way.
 *
 * Callers:
 * - {@link com.korur.oba.apps.otak.documenttemplate.service.impl.DocumentTemplateServiceImpl}:
 *   {@link #toEntity} on create, {@link #toDetailResponse} after every write;
 * - {@link com.korur.oba.apps.otak.documenttemplate.service.impl.DocumentTemplateQueryServiceImpl}:
 *   {@link #toListResponse} for the list, {@link #toDetailResponse} for the detail.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        uses = { BlobUrlRewriterMapper.class })
public interface DocumentTemplateMapper {

    /**
     * Maps a template create request to a new, unsaved entity. The service then writes the
     * trimmed name, the type and the language, and sets the organization from the checked
     * request header, never from the body.
     *
     * The id and the timestamps are set by the database and JPA. The blob URLs are ignored,
     * because a template is always created without files; they arrive through
     * {@link com.korur.oba.apps.otak.documenttemplate.service.DocumentTemplateService#replaceFiles}.
     *
     * Called by
     * {@link com.korur.oba.apps.otak.documenttemplate.service.impl.DocumentTemplateServiceImpl#create}.
     *
     * @param request the validated create request
     * @return a new entity without id, organization or files
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "htmlBlobUrl", ignore = true)
    @Mapping(target = "pdfBlobUrl", ignore = true)
    DocumentTemplate toEntity(DocumentTemplateCreateRequest request);

    /**
     * Maps a template to a list item: the name, type, language, ready flag and last change.
     * The file URLs are left out, so a page of templates stays small and
     * needs no URL signing. The ready flag comes from
     * {@link DocumentTemplate#isReady()}.
     *
     * Called by
     * {@link com.korur.oba.apps.otak.documenttemplate.service.impl.DocumentTemplateQueryServiceImpl#list}.
     *
     * @param template the stored template
     * @return the list item
     */
    DocumentTemplateListResponse toListResponse(DocumentTemplate template);

    /**
     * Maps a template to the detail response, with every field and signed file URLs.
     *
     * The unsigned htmlBlobUrl and pdfBlobUrl are signed by
     * {@link BlobUrlRewriterMapper#rewriteBlobUrl} into htmlUrl and pdfUrl; a null URL
     * stays null. The ready flag comes from {@link DocumentTemplate#isReady()}.
     *
     * Called by
     * {@link com.korur.oba.apps.otak.documenttemplate.service.impl.DocumentTemplateQueryServiceImpl#get}
     * and after every write of
     * {@link com.korur.oba.apps.otak.documenttemplate.service.impl.DocumentTemplateServiceImpl}.
     *
     * @param template the stored template
     * @return the detail response
     */
    @Mapping(target = "htmlUrl", source = "htmlBlobUrl", qualifiedByName = "rewriteBlobUrl")
    @Mapping(target = "pdfUrl", source = "pdfBlobUrl", qualifiedByName = "rewriteBlobUrl")
    DocumentTemplateDetailResponse toDetailResponse(DocumentTemplate template);
}
