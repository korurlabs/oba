package com.korur.oba.apps.kaizen.dailyupdate.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity recording that a member reminded a colleague to post the day's update.
 *
 * One row per recipient, organization and business day, whoever sent it,
 * enforced by the unique key on (organization_id, recipient_id, day), so a
 * member gets at most one nudge push a day however many colleagues tap
 * Nudge. The row is notification state, not content: it is never edited and
 * goes with the organization or either user.
 */
@Entity
@Table(name = "daily_update_nudges", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DailyUpdateNudge extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Organization in which the recipient was nudged.
     */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /**
     * Member who was reminded to post.
     */
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    /**
     * Member who sent the reminder.
     */
    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    /**
     * Business day of the nudge on {@link com.korur.oba.dojo.time.BusinessCalendar}, the
     * day whose update the recipient was reminded of.
     */
    @Column(name = "day", nullable = false)
    private LocalDate day;
}
