package com.korur.oba.apps.kaya.template.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.template.dto.request.TemplateQueryRequest;
import com.korur.oba.apps.kaya.template.dto.response.TemplateDetailResponse;
import com.korur.oba.apps.kaya.template.dto.response.TemplateListResponse;
import com.korur.oba.apps.kaya.template.entity.Template;

/**
 * Reads the {@link Template}s of an organization's versions, and their sizes and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.template.controller.TemplateController} for the
 * Rules page, and by the render and release domains to load the templates of a version.
 */
public interface TemplateQueryService {

    /**
     * Lists the templates of the given versions, ordered by version, path and id. A version id of another organization matches nothing.
     *
     * @param organizationId the organization of the request
     * @param request        the 1 to 20 version ids
     * @return the templates with their byte sizes, without content
     */
    List<TemplateListResponse> getTemplates(UUID organizationId, TemplateQueryRequest request);

    /**
     * Reads one template with its whole file as UTF-8 text.
     *
     * @param organizationId the organization of the request
     * @param templateId     the template
     * @return the template with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such template, or its blob is missing
     */
    TemplateDetailResponse getTemplate(UUID organizationId, UUID templateId);

    /**
     * Loads a template of the organization.
     *
     * A template of another organization answers the same 404 as an unknown id, so its existence is
     * not revealed.
     *
     * @param organizationId the organization of the request
     * @param templateId     the template
     * @return the template
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such template
     */
    Template getEntityById(UUID organizationId, UUID templateId);

    /**
     * Loads every template of one version of the organization, ordered by path.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the templates; empty when the version has none or belongs to another organization
     */
    List<Template> getEntitiesByVersionId(UUID organizationId, UUID versionId);
}
