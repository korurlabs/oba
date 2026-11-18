package com.korur.oba.apps.kaizen.dailyupdate.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal

/**
 * Published when a member nudged a colleague to post the day's update.
 *
 * Consumers act after the transaction commits, so a nudge refused by the
 * once-a-day key sends no push. The sender's name is resolved in the
 * transaction, because the push copy names the sender and the consumer runs on
 * another thread without the request's user.
 *
 * @param organizationId organization in which the recipient was nudged
 * @param nudgeId        the saved nudge
 * @param recipientId    member reminded to post
 * @param senderId       member who sent the reminder
 * @param senderName     display name of the sender, as the push shows it
 * @param day            business day the nudge is for
 */
public record DailyUpdateNudgedEvent(UUID organizationId,
                                     UUID nudgeId,
                                     UUID recipientId,
                                     UUID senderId,
                                     String senderName,
                                     LocalDate day) {
}
