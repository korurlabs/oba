package com.korur.oba.apps.kaya.workflow.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.workflow.dto.request.WorkflowQueryRequest;
import com.korur.oba.apps.kaya.workflow.dto.response.WorkflowDetailResponse;
import com.korur.oba.apps.kaya.workflow.dto.response.WorkflowListResponse;
import com.korur.oba.apps.kaya.workflow.entity.Workflow;

/**
 * Reads the {@link Workflow}s of an organization's versions, and their sizes and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.workflow.controller.WorkflowController} for the Rules page, and by the
 * render and release domains to load the workflows of a version.
 */
public interface WorkflowQueryService {

    /**
     * Lists the workflows of the given versions, ordered by version, path and id, the skill-only
     * workflows without a path last. A version id of
     * another organization matches nothing.
     *
     * @param organizationId the organization of the request
     * @param request        the 1 to 20 version ids
     * @return the workflows with their byte sizes, without content
     */
    List<WorkflowListResponse> getWorkflows(UUID organizationId, WorkflowQueryRequest request);

    /**
     * Reads one workflow with its whole file as UTF-8 text.
     *
     * @param organizationId the organization of the request
     * @param workflowId    the workflow
     * @return the workflow with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such workflow, or its blob is missing
     */
    WorkflowDetailResponse getWorkflow(UUID organizationId, UUID workflowId);

    /**
     * Loads a workflow of the organization.
     *
     * A workflow of another organization answers the same 404 as an unknown id, so its existence is not
     * revealed.
     *
     * @param organizationId the organization of the request
     * @param workflowId    the workflow
     * @return the workflow
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such workflow
     */
    Workflow getEntityById(UUID organizationId, UUID workflowId);

    /**
     * Loads every workflow of one version of the organization, ordered by path.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the workflows; empty when the version has none or belongs to another organization
     */
    List<Workflow> getEntitiesByVersionId(UUID organizationId, UUID versionId);
}
