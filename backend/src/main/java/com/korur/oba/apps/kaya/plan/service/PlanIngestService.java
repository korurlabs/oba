package com.korur.oba.apps.kaya.plan.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.ingest.dto.request.IngestPlanRequest;
import com.korur.oba.apps.kaya.session.entity.Session;

/**
 * The plan write path of a recorder batch. Every method joins the ingest transaction, so a refused
 * batch stores no plan.
 */
public interface PlanIngestService {

    /**
     * Stores the plans of a batch. A SPEC plan is found by the session's repository and its path,
     * shared by every session that writes the file; a SESSION plan by its session. A missing plan is
     * created. A capture whose content hash equals the latest version's, or that is older than the
     * latest version, adds nothing. Otherwise it is the plan's next version: the title, latest
     * session, user and change time follow it, and the review state returns to OPEN. A SPEC plan
     * keeps the pack it names, and takes its feature from the planning handoffs the session holds.
     *
     * @param session the session of the batch, locked by the session ingest
     * @param plans   the plans of the batch in capture order, already validated; null for none
     */
    void storePlans(Session session, List<IngestPlanRequest> plans);

    /**
     * Links SPEC plans to the feature of a planning handoff. When the handoff is a
     * feature/planning.md file whose content names the path of a SPEC plan of the session's
     * repository, that plan's feature becomes the handoff's directory; a later planning handoff
     * that names the same spec replaces it. Any other handoff changes nothing.
     *
     * @param session     the session that wrote the handoff
     * @param handoffPath the path below handoffs/, starting with the feature directory
     * @param content     the handoff's markdown
     */
    void linkFeature(Session session, String handoffPath, String content);
}
