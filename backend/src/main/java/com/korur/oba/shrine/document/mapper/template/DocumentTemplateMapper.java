package com.korur.oba.shrine.document.mapper.template;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.azure.service.BlobUrlRewriterMapper;
import com.korur.oba.shrine.document.dto.request.template.DocumentTemplateCreateRequest;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateDetailResponse;
import com.korur.oba.shrine.document.dto.response.template.DocumentTemplateListResponse;
import com.korur.oba.shrine.document.entity.template.DocumentTemplate;

/**
 * Maps a {@link DocumentTemplateCreateRequest} to a new {@link DocumentTemplate}, and a
 * template to a {@link DocumentTemplateListResponse} or a
 * {@link DocumentTemplateDetailResponse}. MapStruct generates the implementation as a
 * Spring bean with constructor injection.
 *
 * A template has no association to another entity (its organization and legal entity are
 * plain ids), so the mapper needs no MapperHelper. The short name of the legal entity comes
 * from the legal entity domain, read by the services through
 * {@link com.korur.oba.shrine.legalentity.service.LegalEntityQueryService}, and is passed in
 * as a second source.
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
 * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl},
 * so both store the same columns the same way.
 *
 * Callers:
 * - {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl}:
 *   {@link #toEntity} on create, {@link #toDetailResponse} after every write;
 * - {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateQueryServiceImpl}:
 *   {@link #toListResponse} for the list, {@link #toDetailResponse} for the detail.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        uses = { BlobUrlRewriterMapper.class })
public interface DocumentTemplateMapper {

    /**
     * Maps a template create request to a new, unsaved entity. The service then writes the
     * trimmed name, the type, the language and the checked legal entity, sets the
     * organization from the checked request header, never from the body, and the authors
     * from the caller.
     *
     * The id and the timestamps are set by the database and JPA. The blob URLs are ignored,
     * because a template is always created without files; they arrive through
     * {@link com.korur.oba.shrine.document.service.template.DocumentTemplateService#replaceFiles}.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl#create}.
     *
     * @param request the validated create request
     * @return a new entity without id, organization or files
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "legalEntityId", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "updatedById", ignore = true)
    @Mapping(target = "htmlBlobUrl", ignore = true)
    @Mapping(target = "pdfBlobUrl", ignore = true)
    DocumentTemplate toEntity(DocumentTemplateCreateRequest request);

    /**
     * Maps a template to a list item: the name, type, language, legal entity, ready flag and
     * last change.
     * The file URLs are left out, so a page of templates stays small and
     * needs no URL signing. The ready flag comes from
     * {@link DocumentTemplate#isReady()}.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateQueryServiceImpl#list}.
     *
     * @param template             the stored template
     * @param legalEntityShortName the short name of the template's legal entity, or null when it has none
     * @return the list item
     */
    @Mapping(target = "legalEntityShortName", source = "legalEntityShortName")
    DocumentTemplateListResponse toListResponse(DocumentTemplate template, String legalEntityShortName);

    /**
     * Maps a template to the detail response, with every field and signed file URLs.
     *
     * The unsigned htmlBlobUrl and pdfBlobUrl are signed by
     * {@link BlobUrlRewriterMapper#rewriteBlobUrl} into htmlUrl and pdfUrl; a null URL
     * stays null. The ready flag comes from {@link DocumentTemplate#isReady()}.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateQueryServiceImpl#get}
     * and after every write of
     * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl}.
     *
     * @param template             the stored template
     * @param legalEntityShortName the short name of the template's legal entity, or null when it has none
     * @return the detail response
     */
    @Mapping(target = "htmlUrl", source = "template.htmlBlobUrl", qualifiedByName = "rewriteBlobUrl")
    @Mapping(target = "pdfUrl", source = "template.pdfBlobUrl", qualifiedByName = "rewriteBlobUrl")
    @Mapping(target = "legalEntityShortName", source = "legalEntityShortName")
    DocumentTemplateDetailResponse toDetailResponse(DocumentTemplate template, String legalEntityShortName);
}
