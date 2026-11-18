package com.korur.oba.apps.kaizen.dailyupdate.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdateNudge;

/**
 * Repository interface for {@link DailyUpdateNudge} entity database operations.
 *
 * The unique key daily_update_nudges_organization_id_recipient_id_day_uk is
 * the final guard of the once-a-day rule: the existence check below answers
 * the common case, and a concurrent nudge that passes it is refused by the
 * key when the row is flushed.
 */
@Repository
public interface DailyUpdateNudgeRepository extends JpaRepository<DailyUpdateNudge, UUID>,
        JpaSpecificationExecutor<DailyUpdateNudge> {

    /**
     * Tells whether the recipient was already nudged in the organization on the day.
     *
     * @param organizationId the organization
     * @param recipientId    the nudged member
     * @param day            the business day
     * @return true when a nudge row exists
     */
    boolean existsByOrganizationIdAndRecipientIdAndDay(UUID organizationId, UUID recipientId, LocalDate day);
}
