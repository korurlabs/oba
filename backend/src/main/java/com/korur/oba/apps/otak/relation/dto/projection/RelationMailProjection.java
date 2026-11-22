package com.korur.oba.apps.otak.relation.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * One mail of a relation with the title of the outreach that sends it, a row of the native
 * query {@link com.korur.oba.apps.otak.relation.repository.RelationRepository#findMails}. The
 * getter names match the aliases of that query.
 */
public interface RelationMailProjection {

    /**
     * @return the mail id
     */
    UUID getMailId();

    /**
     * @return the title of the outreach that sends the mail, or null when no outreach names it
     */
    String getOutreachTitle();
}
